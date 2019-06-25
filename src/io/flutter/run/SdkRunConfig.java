/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementAdapter;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.jetbrains.lang.dart.ide.runner.DartConsoleFilter;
import io.flutter.FlutterUtils;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.sdk.FlutterSdkManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;

/**
 * Run configuration used for launching a Flutter app using the Flutter SDK.
 */
public class SdkRunConfig extends LocatableConfigurationBase
  implements LaunchState.RunConfig, RefactoringListenerProvider, RunConfigurationWithSuppressedDefaultRunAction {

  private static final Logger LOG = Logger.getInstance(SdkRunConfig.class);

  private @NotNull SdkFields fields = new SdkFields();

  public SdkRunConfig(final @NotNull Project project, final @NotNull ConfigurationFactory factory, final @NotNull String name) {
    super(project, factory, name);
  }

  @NotNull
  public SdkFields getFields() {
    return fields;
  }

  public void setFields(@NotNull SdkFields newFields) {
    fields = newFields;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    fields.checkRunnable(getProject());
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new FlutterConfigurationEditorForm(getProject());
  }

  public static class RecursiveDeleter
    extends SimpleFileVisitor<Path> {

    private final PathMatcher matcher;

    RecursiveDeleter(String pattern) {
      matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      final Path name = file.getFileName();
      if (name != null && matcher.matches(name)) {
        try {
          Files.delete(file);
        }
        catch (IOException e) {
          FlutterUtils.warn(LOG, e);
          // TODO(jacobr): consider aborting.
        }
      }
      return CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      FlutterUtils.warn(LOG, exc);
      return CONTINUE;
    }
  }

  @NotNull
  @Override
  public LaunchState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final SdkFields launchFields = fields.copy();
    try {
      launchFields.checkRunnable(env.getProject());
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final MainFile mainFile = MainFile.verify(launchFields.getFilePath(), env.getProject()).get();
    final Project project = env.getProject();
    final RunMode mode = RunMode.fromEnv(env);
    final Module module = ModuleUtilCore.findModuleForFile(mainFile.getFile(), env.getProject());
    final LaunchState.CreateAppCallback createAppCallback = (@Nullable FlutterDevice device) -> {
      // Up until the support for Flutter Web, 'device' was checked for null and returned. The device
      // can only be null if this is a Flutter Web execution; this expecation is checked elsewhere.

      final GeneralCommandLine command = getCommand(env, device);

      // Workaround for https://github.com/flutter/flutter/issues/16766
      // TODO(jacobr): remove once flutter tool incremental building works
      // properly with --track-widget-creation.
      final Path buildPath = command.getWorkDirectory().toPath().resolve("build");
      final Path cachedParametersPath = buildPath.resolve("last_build_run.json");
      final String[] parametersToTrack = {"--preview-dart-2", "--track-widget-creation"};
      final JsonArray jsonArray = new JsonArray();
      for (String parameter : command.getParametersList().getList()) {
        for (String allowedParameter : parametersToTrack) {
          if (parameter.startsWith(allowedParameter)) {
            jsonArray.add(new JsonPrimitive(parameter));
            break;
          }
        }
      }
      final String json = new Gson().toJson(jsonArray);
      String existingJson = null;
      if (Files.exists(cachedParametersPath)) {
        try {
          existingJson = new String(Files.readAllBytes(cachedParametersPath), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
          FlutterUtils.warn(LOG, "Unable to get existing json from " + cachedParametersPath);
        }
      }
      if (!StringUtil.equals(json, existingJson)) {
        // We don't have proof the current run is consistent with the existing run.
        // Be safe and delete cached files that could cause problems due to
        // https://github.com/flutter/flutter/issues/16766
        // We could just delete app.dill and snapshot_blob.bin.d.fingerprint
        // but it is safer to just delete everything as we won't be broken by future changes
        // to the underlying Flutter build rules.
        try {
          if (Files.exists(buildPath)) {
            if (Files.isDirectory(buildPath)) {
              Files.walkFileTree(buildPath, new RecursiveDeleter("*.{fingerprint,dill}"));
            }
          }
          else {
            Files.createDirectory(buildPath);
          }
          Files.write(cachedParametersPath, json.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
          FlutterUtils.warn(LOG, e);
        }
      }

      final FlutterApp app = FlutterApp.start(env, project, module, mode, device, command,
                                              StringUtil.capitalize(mode.mode()) + "App",
                                              "StopApp");

      // Stop the app if the Flutter SDK changes.
      final FlutterSdkManager.Listener sdkListener = new FlutterSdkManager.Listener() {
        @Override
        public void flutterSdkRemoved() {
          app.shutdownAsync();
        }
      };
      FlutterSdkManager.getInstance(project).addListener(sdkListener);
      Disposer.register(project, () -> FlutterSdkManager.getInstance(project).removeListener(sdkListener));

      return app;
    };

    final LaunchState launcher = new LaunchState(env, mainFile.getAppDir(), mainFile.getFile(), this, createAppCallback);
    addConsoleFilters(launcher, env, mainFile, module);
    return launcher;
  }

  protected void addConsoleFilters(@NotNull LaunchState launcher,
                                   @NotNull ExecutionEnvironment env,
                                   @NotNull MainFile mainFile,
                                   @Nullable Module module) {
    // Set up additional console filters.
    final TextConsoleBuilder builder = launcher.getConsoleBuilder();
    builder.addFilter(new DartConsoleFilter(env.getProject(), mainFile.getFile()));

    if (module != null) {
      builder.addFilter(new FlutterConsoleFilter(module));
    }
    builder.addFilter(new UrlFilter());
  }

  @NotNull
  @Override
  public GeneralCommandLine getCommand(@NotNull ExecutionEnvironment env, @Nullable FlutterDevice device) throws ExecutionException {
    final SdkFields launchFields = fields.copy();
    final Project project = env.getProject();
    final RunMode mode = RunMode.fromEnv(env);
    return fields.createFlutterSdkRunCommand(project, mode, FlutterLaunchMode.fromEnv(env), device);
  }

  @Override
  @Nullable
  public String suggestedName() {
    final String filePath = fields.getFilePath();
    return filePath == null ? null : PathUtil.getFileName(filePath);
  }

  @Override
  public SdkRunConfig clone() {
    final SdkRunConfig clone = (SdkRunConfig)super.clone();
    clone.fields = fields.copy();
    return clone;
  }

  @Override
  public void writeExternal(@NotNull final Element element) throws WriteExternalException {
    super.writeExternal(element);
    XmlSerializer.serializeInto(getFields(), element, new SkipDefaultValuesSerializationFilters());
  }

  @Override
  public void readExternal(@NotNull final Element element) throws InvalidDataException {
    super.readExternal(element);
    XmlSerializer.deserializeInto(getFields(), element);
  }

  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    final String filePath = getFields().getFilePath();
    if (filePath == null) return null;

    final String affectedPath = getAffectedPath(element);
    if (affectedPath == null) return null;

    if (element instanceof PsiFile && filePath.equals(affectedPath)) {
      return new RenameRefactoringListener(affectedPath);
    }

    if (element instanceof PsiDirectory && filePath.startsWith(affectedPath + "/")) {
      return new RenameRefactoringListener(affectedPath);
    }

    return null;
  }

  private String getAffectedPath(PsiElement element) {
    if (!(element instanceof PsiFileSystemItem)) return null;
    final VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
    return file == null ? null : file.getPath();
  }

  private class RenameRefactoringListener extends UndoRefactoringElementAdapter {
    private @NotNull String myAffectedPath;

    private RenameRefactoringListener(final @NotNull String affectedPath) {
      myAffectedPath = affectedPath;
    }

    private String getNewPathAndUpdateAffectedPath(final @NotNull PsiElement newElement) {
      final String oldPath = fields.getFilePath();

      final VirtualFile newFile = newElement instanceof PsiFileSystemItem ? ((PsiFileSystemItem)newElement).getVirtualFile() : null;
      if (newFile != null && oldPath != null && oldPath.startsWith(myAffectedPath)) {
        final String newPath = newFile.getPath() + oldPath.substring(myAffectedPath.length());
        myAffectedPath = newFile.getPath(); // needed if refactoring will be undone
        return newPath;
      }

      return oldPath;
    }

    @Override
    protected void refactored(@NotNull final PsiElement element, @Nullable final String oldQualifiedName) {
      final boolean generatedName = getName().equals(suggestedName());
      final String filePath = fields.getFilePath();

      final String newPath = getNewPathAndUpdateAffectedPath(element);
      fields.setFilePath(newPath);

      if (generatedName) {
        setGeneratedName();
      }
    }
  }
}

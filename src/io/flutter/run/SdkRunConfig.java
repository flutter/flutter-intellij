/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
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
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterSdkManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration used for launching a Flutter app using the Flutter SDK.
 */
public class SdkRunConfig extends LocatableConfigurationBase
  implements Launcher.RunConfig, RefactoringListenerProvider, RunConfigurationWithSuppressedDefaultRunAction {
  private @NotNull SdkFields fields = new SdkFields();

  SdkRunConfig(final @NotNull Project project, final @NotNull ConfigurationFactory factory, final @NotNull String name) {
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

  @NotNull
  @Override
  public Launcher getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final SdkFields launchFields = fields.copy();
    try {
      launchFields.checkRunnable(env.getProject());
    } catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final MainFile main = MainFile.verify(launchFields.getFilePath(), env.getProject()).get();
    final Project project = env.getProject();
    final RunMode mode = RunMode.fromEnv(env);

    final Launcher.Callback callback = (device) -> {
      final GeneralCommandLine command = fields.createFlutterSdkRunCommand(project, device, mode);
      final FlutterApp app = FlutterApp.start(project, mode, command,
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

    final Launcher launcher = new Launcher(env, main.getAppDir(), main.getFile(), this, callback);

    // Set up additional console filters.
    final TextConsoleBuilder builder = launcher.getConsoleBuilder();
    builder.addFilter(new DartConsoleFilter(env.getProject(), main.getFile()));

    final Module module = ModuleUtil.findModuleForFile(main.getFile(), env.getProject());
    if (module != null) {
      builder.addFilter(new FlutterConsoleFilter(module));
    }

    return launcher;
  }

  @Nullable
  public String suggestedName() {
    final String filePath = fields.getFilePath();
    return filePath == null ? null : PathUtil.getFileName(filePath);
  }

  public SdkRunConfig clone() {
    final SdkRunConfig clone = (SdkRunConfig)super.clone();
    clone.fields = fields.copy();
    return clone;
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    XmlSerializer.serializeInto(getFields(), element, new SkipDefaultValuesSerializationFilters());
  }

  @Override
  public void readExternal(final Element element) throws InvalidDataException {
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

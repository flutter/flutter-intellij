/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.attach;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.jetbrains.lang.dart.ide.runner.DartConsoleFilter;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.run.LaunchState;
import io.flutter.run.MainFile;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterSdkManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Similar to SdkRunConfig
public class SdkAttachConfig
  extends LocatableConfigurationBase
  implements LaunchState.RunConfig, RefactoringListenerProvider, RunConfigurationWithSuppressedDefaultRunAction {

  private static final Logger LOG = Logger.getInstance(SdkAttachConfig.class);
  private @NotNull SdkFields fields = new SdkFields(); // TODO(messick): Delete if not needed.

  SdkAttachConfig(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
    super(project, factory, name);
  }

  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    return null;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    throw new IllegalStateException("Attach configurations are not editable");
  }

  @NotNull
  @Override
  public LaunchState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    try {
      checkRunnable(env.getProject());
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    SdkFields launchFields = fields;
    MainFile mainFile = MainFile.verify(launchFields.getFilePath(), env.getProject()).get();
    Project project = env.getProject();
    RunMode mode = RunMode.fromEnv(env);
    Module module = ModuleUtilCore.findModuleForFile(mainFile.getFile(), env.getProject());
    LaunchState.Callback callback = (device) -> {
      if (device == null) return null;

      GeneralCommandLine command = getCommand(env, device);
      {
        // Workaround for https://github.com/flutter/flutter/issues/16766
        // TODO(jacobr): remove once flutter tool incremental building works
        // properly with --track-widget-creation.
        Path buildPath = command.getWorkDirectory().toPath().resolve("build");
        Path cachedParametersPath = buildPath.resolve("last_build_run.json");
        String[] parametersToTrack = {"--preview-dart-2", "--track-widget-creation"};
        JsonArray jsonArray = new JsonArray();
        for (String parameter : command.getParametersList().getList()) {
          for (String allowedParameter : parametersToTrack) {
            if (parameter.startsWith(allowedParameter)) {
              jsonArray.add(new JsonPrimitive(parameter));
              break;
            }
          }
        }
        String json = new Gson().toJson(jsonArray);
        String existingJson = null;
        if (Files.exists(cachedParametersPath)) {
          try {
            existingJson = new String(Files.readAllBytes(cachedParametersPath), StandardCharsets.UTF_8);
          }
          catch (IOException e) {
            LOG.warn("Unable to get existing json from " + cachedParametersPath);
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
                Files.walkFileTree(buildPath, new SdkRunConfig.RecursiveDeleter("*.{fingerprint,dill}"));
              }
            }
            else {
              Files.createDirectory(buildPath);
            }
            Files.write(cachedParametersPath, json.getBytes(StandardCharsets.UTF_8));
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }

      FlutterApp app = FlutterApp.start(env, project, module, mode, device, command,
                                              StringUtil.capitalize(mode.mode()) + "App",
                                              "StopApp");

      // Stop the app if the Flutter SDK changes.
      FlutterSdkManager.Listener sdkListener = new FlutterSdkManager.Listener() {
        @Override
        public void flutterSdkRemoved() {
          app.shutdownAsync();
        }
      };
      FlutterSdkManager.getInstance(project).addListener(sdkListener);
      Disposer.register(project, () -> FlutterSdkManager.getInstance(project).removeListener(sdkListener));

      return app;
    };

    LaunchState launcher = new LaunchState(env, mainFile.getAppDir(), mainFile.getFile(), this, callback);

    // Set up additional console filters.
    TextConsoleBuilder builder = launcher.getConsoleBuilder();
    builder.addFilter(new DartConsoleFilter(env.getProject(), mainFile.getFile()));

    if (module != null) {
      builder.addFilter(new FlutterConsoleFilter(module));
    }

    return launcher;
  }

  @NotNull
  @Override
  public GeneralCommandLine getCommand(ExecutionEnvironment environment, FlutterDevice device) throws ExecutionException {
    return null;
  }

  private void checkRunnable(@NotNull Project project) throws RuntimeConfigurationError {
    DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }
    MainFile.Result main = MainFile.verify(fields.getFilePath(), project);
    if (!main.canLaunch()) {
      throw new RuntimeConfigurationError(main.getError());
    }
    if (PubRoot.forDirectory(main.get().getAppDir()) == null) {
      throw new RuntimeConfigurationError("Entrypoint isn't within a Flutter pub root");
    }
  }
}

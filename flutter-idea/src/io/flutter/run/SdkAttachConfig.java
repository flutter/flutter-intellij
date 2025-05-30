/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.run.common.RunMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdkAttachConfig extends SdkRunConfig {

  public @Nullable PubRoot pubRoot;

  public SdkAttachConfig(SdkRunConfig config) {
    //noinspection ConstantConditions
    super(config.getProject(), config.getFactory(), config.getName());
    setFields(config.getFields()); // TODO(messick): Delete if not needed.
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

    final SdkFields launchFields = getFields();
    final MainFile mainFile = MainFile.verify(launchFields.getFilePath(), env.getProject()).get();
    final Project project = env.getProject();
    final RunMode mode = RunMode.fromEnv(env);
    final Module module = ModuleUtilCore.findModuleForFile(mainFile.getFile(), env.getProject());
    final LaunchState.CreateAppCallback createAppCallback = (@Nullable FlutterDevice device) -> {
      if (device == null) return null;

      final GeneralCommandLine command = getCommand(env, device);
      return getFlutterApp(env, device, project, module, mode, command);
    };

    final LaunchState launcher = new AttachState(env, mainFile.getAppDir(), mainFile.getFile(), this, createAppCallback);
    addConsoleFilters(launcher, env, mainFile, module);
    return launcher;
  }

  @NotNull
  @Override
  public GeneralCommandLine getCommand(@NotNull ExecutionEnvironment env, @NotNull FlutterDevice device) throws ExecutionException {
    return getFields().createFlutterSdkAttachCommand(env.getProject(), FlutterLaunchMode.fromEnv(env), device);
  }

  private void checkRunnable(@NotNull Project project) throws RuntimeConfigurationError {
    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }
    final MainFile.Result main = MainFile.verify(getFields().getFilePath(), project);
    if (!main.canLaunch()) {
      throw new RuntimeConfigurationError(main.getError());
    }
    if (PubRoot.forDirectory(main.get().getAppDir()) == null) {
      throw new RuntimeConfigurationError("Entrypoint isn't within a Flutter pub root");
    }
  }
}

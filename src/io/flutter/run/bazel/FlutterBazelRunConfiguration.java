/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import io.flutter.run.FlutterRunConfigurationBase;
import io.flutter.run.FlutterRunnerParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterBazelRunConfiguration extends FlutterRunConfigurationBase implements RunConfigurationWithSuppressedDefaultRunAction {
  private @NotNull FlutterRunnerParameters myRunnerParameters = new FlutterRunnerParameters();

  public FlutterBazelRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
    super(project, factory, name);
  }

  @NotNull
  public FlutterRunnerParameters getRunnerParameters() {
    return myRunnerParameters;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    getRunnerParameters().checkForBazelLaunch(getProject());
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new FlutterBazelConfigurationEditorForm(getProject());
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return new FlutterBazelAppState(environment);
  }

  @Nullable
  public String suggestedName() {
    return myRunnerParameters.getBazelTarget();
  }

  public FlutterBazelRunConfiguration clone() {
    final FlutterBazelRunConfiguration clone = (FlutterBazelRunConfiguration)super.clone();
    clone.myRunnerParameters = myRunnerParameters.clone();
    return clone;
  }
}

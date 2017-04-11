/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterInitializer;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public abstract class RunFlutterAction extends AnAction {

  private final @NotNull String myRunArg;
  private final @NotNull String myExecutorId;

  public RunFlutterAction(@NotNull String text,
                          @NotNull String description,
                          @NotNull Icon icon,
                          @NotNull String runArg,
                          @NotNull String executorId) {
    super(text, description, icon);
    myRunArg = runArg;
    myExecutorId = executorId;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FlutterInitializer.sendAnalyticsAction(this);

    final RunnerAndConfigurationSettings settings = getRunConfigSettings(e);
    if (settings == null) {
      return;
    }

    final RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof SdkRunConfig)) {
      // Action is disabled; shouldn't happen.
      return;
    }

    final SdkRunConfig sdkRunConfig = (SdkRunConfig)configuration.clone();
    final SdkFields fields = sdkRunConfig.getFields();
    final String additionalArgs = fields.getAdditionalArgs();
    if (additionalArgs == null) {
      fields.setAdditionalArgs(myRunArg);
    }
    else {
      if (!additionalArgs.contains(myRunArg)) {
        fields.setAdditionalArgs(additionalArgs + myRunArg);
      }
    }

    final Executor executor = getExecutor(myExecutorId);
    if (executor == null) {
      return;
    }

    final ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, sdkRunConfig);
    final ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Override
  public void update(AnActionEvent e) {
    final RunnerAndConfigurationSettings settings = getRunConfigSettings(e);
    // TODO(pq): add support for Bazel.
    final boolean enabled = settings != null && (settings.getConfiguration() instanceof SdkRunConfig);
    e.getPresentation().setEnabled(enabled);
  }

  @Nullable
  private static RunnerAndConfigurationSettings getRunConfigSettings(@Nullable AnActionEvent e) {
    if (e == null) return null;
    final Project project = e.getProject();
    if (project == null) {
      return null;
    }

    return RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
  }

  @Nullable
  private static Executor getExecutor(@NotNull String executorId) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
      if (executorId.equals(executor.getId())) {
        return executor;
      }
    }

    return null;
  }
}

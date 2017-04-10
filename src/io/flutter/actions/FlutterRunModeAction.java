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


public abstract class FlutterRunModeAction extends AnAction {

  private final @NotNull String myRunArg;
  private final @NotNull String myExecutorId;

  public FlutterRunModeAction(@NotNull String text,
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

    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      return;
    }

    final RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof SdkRunConfig)) {
      //TODO: supported in Bazel?
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

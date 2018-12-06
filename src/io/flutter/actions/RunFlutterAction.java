/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.google.common.base.Joiner;
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
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.LaunchState;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class RunFlutterAction extends AnAction {
  private final @NotNull String myDetailedTextKey;
  private final @NotNull FlutterLaunchMode myLaunchMode;
  private final @NotNull String myExecutorId;

  public RunFlutterAction(@NotNull String text,
                          @NotNull String detailedTextKey,
                          @NotNull String description,
                          @NotNull Icon icon,
                          @NotNull FlutterLaunchMode launchMode,
                          @NotNull String executorId) {
    super(text, description, icon);

    myDetailedTextKey = detailedTextKey;
    myLaunchMode = launchMode;
    myExecutorId = executorId;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // NOTE: When making changes here, consider making similar changes to ConnectAndroidDebuggerAction.
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

    String flavorArg = null;
    if (fields.getBuildFlavor() != null) {
      flavorArg = "--flavor=" + fields.getBuildFlavor();
    }

    final List<String> args = new ArrayList<>();
    if (additionalArgs != null) {
      args.add(additionalArgs);
    }
    if (flavorArg != null) {
      args.add(flavorArg);
    }
    if (!args.isEmpty()) {
      fields.setAdditionalArgs(Joiner.on(" ").join(args));
    }

    final Executor executor = getExecutor(myExecutorId);
    if (executor == null) {
      return;
    }

    final ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, sdkRunConfig);

    final ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();
    FlutterLaunchMode.addToEnvironment(env, myLaunchMode);

    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // Text.
    final String config = getSelectedRunConfig(e);
    final String message =
      config != null ? FlutterBundle.message(myDetailedTextKey, config) : FlutterBundle.message("app.profile.action.text");
    e.getPresentation().setText(message);

    // Enablement.
    e.getPresentation().setEnabled(shouldEnable(e));
  }

  private static boolean shouldEnable(@Nullable AnActionEvent e) {
    final RunnerAndConfigurationSettings settings = getRunConfigSettings(e);
    final RunConfiguration config = settings == null ? null : settings.getConfiguration();
    // TODO(pq): add support for Bazel.
    return config instanceof SdkRunConfig && LaunchState.getRunningAppProcess((SdkRunConfig)config) == null;
  }

  @Nullable
  protected static String getSelectedRunConfig(@Nullable AnActionEvent e) {
    final RunnerAndConfigurationSettings settings = getRunConfigSettings(e);
    if (settings != null) {
      final RunConfiguration configuration = settings.getConfiguration();
      if (configuration != null) {
        return configuration.getName();
      }
    }
    return null;
  }

  @Nullable
  public static RunnerAndConfigurationSettings getRunConfigSettings(@Nullable AnActionEvent event) {
    if (event == null) {
      return null;
    }

    final Project project = event.getProject();
    if (project == null) {
      return null;
    }

    return RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
  }

  @Nullable
  public static Executor getExecutor(@NotNull String executorId) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
      if (executorId.equals(executor.getId())) {
        return executor;
      }
    }

    return null;
  }
}

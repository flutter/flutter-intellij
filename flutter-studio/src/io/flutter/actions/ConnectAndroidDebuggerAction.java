/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import org.jetbrains.android.actions.AndroidConnectDebuggerAction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ConnectAndroidDebuggerAction extends AndroidConnectDebuggerAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    if (!FlutterUtils.isAndroidStudio()) {
      super.actionPerformed(e);
      return;
    }
    FlutterInitializer.sendAnalyticsAction(this);

    RunnerAndConfigurationSettings settings = RunFlutterAction.getRunConfigSettings(e);
    if (settings == null) {
      return;
    }

    RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof SdkRunConfig)) {
      // Action is disabled; shouldn't happen.
      return;
    }

    SdkRunConfig sdkRunConfig = (SdkRunConfig)configuration.clone();
    SdkFields fields = sdkRunConfig.getFields();
    String additionalArgs = fields.getAdditionalArgs();

    String flavorArg = null;
    if (fields.getBuildFlavor() != null) {
      flavorArg = "--flavor=" + fields.getBuildFlavor();
    }

    List<String> args = new ArrayList<>();
    if (additionalArgs != null) {
      args.add(additionalArgs);
    }
    if (flavorArg != null) {
      args.add(flavorArg);
    }
    if (!args.isEmpty()) {
      fields.setAdditionalArgs(Joiner.on(" ").join(args));
    }

    Executor executor = RunFlutterAction.getExecutor(ToolWindowId.RUN);
    if (executor == null) {
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, sdkRunConfig);

    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();
    FlutterLaunchMode.addToEnvironment(env, FlutterLaunchMode.ATTACH);

    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Override
  public void update(AnActionEvent e) {
    // TODO(messick): Remove this method if there is no special update requirement.
    if (!FlutterUtils.isAndroidStudio()) {
      super.update(e);
      return;
    }
    super.update(e);
  }
}


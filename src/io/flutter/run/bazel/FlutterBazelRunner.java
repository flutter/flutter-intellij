/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.run.FlutterRunConfigurationBase;
import io.flutter.run.FlutterRunner;
import org.jetbrains.annotations.NotNull;

public class FlutterBazelRunner extends FlutterRunner {
  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterBazelRunner";
  }

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
    if (!(profile instanceof FlutterBazelRunConfiguration)) {
      return false;
    }

    final FlutterRunConfigurationBase runConfiguration = (FlutterRunConfigurationBase)profile;
    final Project project = runConfiguration.getProject();

    //noinspection SimplifiableIfStatement
    if (DartSdk.getDartSdk(project) == null) {
      return false;
    }

    //noinspection SimplifiableIfStatement
    if (hasAnyRunningConfigs(runConfiguration)) {
      return false;
    }

    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId) || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId);
  }
}

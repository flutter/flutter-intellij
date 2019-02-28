/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import io.flutter.FlutterUtils;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

/**
 * Runner for non-Bazel run configurations (using the Flutter SDK).
 */
public class FlutterRunner extends LaunchState.Runner<SdkRunConfig> {
  public FlutterRunner() {
    super(SdkRunConfig.class);
  }

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterRunner";
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    if (FlutterUtils.is2018_3_or_higher()) {
      // Force "allow running in parallel" (see: #2875).
      // TODO(pq): when 2018.3 is our lower bound, migrate to using `RunConfigurationSingletonPolicy` (see: #2897).
      final RunnerAndConfigurationSettings settings = env.getRunnerAndConfigurationSettings();
      if (settings != null) {
        settings.setSingleton(false);
      }
    }
    return super.doExecute(state, env);
  }

  @Override
  public boolean canRun(SdkRunConfig config) {
    return FlutterSdk.getFlutterSdk(config.getProject()) != null;
  }
}

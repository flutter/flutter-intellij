/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import io.flutter.run.LaunchState;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The IntelliJ launch mode.
 */
public enum RunMode {
  @NonNls
  DEBUG(DefaultDebugExecutor.EXECUTOR_ID, true),

  @NonNls
  RUN(DefaultRunExecutor.EXECUTOR_ID, true),

  @NonNls
  PROFILE("PROFILE", false);

  private final String myModeString;
  private final boolean mySupportsReload;

  RunMode(String modeString, boolean supportsReload) {
    myModeString = modeString;
    mySupportsReload = supportsReload;
  }

  public String mode() {
    return myModeString;
  }

  public boolean supportsReload() {
    return mySupportsReload;
  }

  public boolean isProfiling() {
    return this == PROFILE;
  }

  @NotNull
  public static RunMode fromEnv(@NotNull ExecutionEnvironment env) throws ExecutionException {
    final String mode = env.getExecutor().getId();
    switch (mode) {
      case DefaultRunExecutor.EXECUTOR_ID:
        return RUN;
      case DefaultDebugExecutor.EXECUTOR_ID:
        return DEBUG;
      case LaunchState.ANDROID_PROFILER_EXECUTOR_ID:
        return PROFILE;
      default:
        throw new ExecutionException("unsupported run mode: " + mode);
    }
  }
}

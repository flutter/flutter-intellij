/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The IntelliJ launch mode.
 */
public enum RunMode {
  @NonNls
  DEBUG(DefaultDebugExecutor.EXECUTOR_ID),

  @NonNls
  RUN(DefaultRunExecutor.EXECUTOR_ID);

  private final String myModeString;

  RunMode(String modeString) {
    myModeString = modeString;
  }

  public String mode() {
    return myModeString;
  }

  @NotNull
  public static RunMode fromEnv(@NotNull ExecutionEnvironment env) throws ExecutionException {
    final String mode = env.getExecutor().getId();
    if (DefaultRunExecutor.EXECUTOR_ID.equals(mode)) {
      return RUN;
    }
    else if (DefaultDebugExecutor.EXECUTOR_ID.equals(mode)) {
      return DEBUG;
    }
    else {
      throw new ExecutionException("unsupported run mode: " + mode);
    }
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class SystemUtils {

  /**
   * Locate a given command-line tool given its name.
   * <p>
   * This is used to locate binaries that are not pre-installed. If it is necessary to find pre-installed
   * binaries it will require more work, especially on Windows.
   */
  @Nullable
  public static String which(String toolName) {
    final File gitExecutableFromPath =
      PathEnvironmentVariableUtil.findInPath(SystemInfo.isWindows ? toolName + ".exe" : toolName, getPath(), null);
    if (gitExecutableFromPath != null) {
      return gitExecutableFromPath.getAbsolutePath();
    }
    return null;
  }

  @Nullable
  private static String getPath() {
    return PathEnvironmentVariableUtil.getPathVariableValue();
  }

  /**
   * Execute the given command line, and return the process output as one result in a future.
   * <p>
   * This is a non-blocking equivalient to {@link ExecUtil#execAndGetOutput(GeneralCommandLine)}.
   */
  public static CompletableFuture<ProcessOutput> execAndGetOutput(GeneralCommandLine cmd) {
    final CompletableFuture<ProcessOutput> future = new CompletableFuture<>();

    AppExecutorUtil.getAppExecutorService().submit(() -> {
      try {
        final ProcessOutput output = ExecUtil.execAndGetOutput(cmd);
        future.complete(output);
      }
      catch (ExecutionException e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }
}

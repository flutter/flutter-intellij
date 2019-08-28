/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class SystemUtils {
  /**
   * Locate a given command-line tool given its name.
   */
  @Nullable
  public static String which(String toolName) {
    final String whichCommandName = SystemInfo.isWindows ? "where" : "which";

    final GeneralCommandLine cmd = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withExePath(whichCommandName)
      .withParameters(toolName);

    try {
      final StringBuilder stringBuilder = new StringBuilder();
      final OSProcessHandler process = new OSProcessHandler(cmd);
      process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          if (outputType == ProcessOutputTypes.STDOUT) {
            stringBuilder.append(event.getText());
          }
        }
      });
      process.startNotify();

      // We wait a maximum of 2000ms.
      if (!process.waitFor(2000)) {
        return null;
      }

      final Integer exitCode = process.getExitCode();
      if (exitCode == null || process.getExitCode() != 0) {
        return null;
      }

      final String[] results = stringBuilder.toString().split("\n");
      return results.length == 0 ? null : results[0].trim();
    }
    catch (ExecutionException | RuntimeException e) {
      return null;
    }
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

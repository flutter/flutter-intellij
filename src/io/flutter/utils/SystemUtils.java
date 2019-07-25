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

  /**
   * Copied from VfsUtilCore because we need to build for 2017.3 (see below, @since).
   * TODO(anyone): Delete this method after 2017.3 is no longer supported (i.e. when AS 3.2 is stable).
   * <p>
   * Returns the relative path from one virtual file to another.
   * If {@code src} is a file, the path is calculated from its parent directory.
   *
   * @param src           the file or directory, from which the path is built
   * @param dst           the file or directory, to which the path is built
   * @param separatorChar the separator for the path components
   * @return the relative path, or {@code null} if the files have no common ancestor
   * @since 2018.1
   */
  @Nullable
  public static String findRelativePath(@NotNull VirtualFile src, @NotNull VirtualFile dst, char separatorChar) {
    if (!src.getFileSystem().equals(dst.getFileSystem())) {
      return null;
    }

    if (!src.isDirectory()) {
      src = src.getParent();
      if (src == null) return null;
    }

    final VirtualFile commonAncestor = VfsUtilCore.getCommonAncestor(src, dst);
    if (commonAncestor == null) return null;

    final StringBuilder buffer = new StringBuilder();

    if (!Comparing.equal(src, commonAncestor)) {
      while (!Comparing.equal(src, commonAncestor)) {
        buffer.append("..").append(separatorChar);
        src = src.getParent();
      }
    }

    buffer.append(VfsUtilCore.getRelativePath(dst, commonAncestor, separatorChar));

    if (StringUtil.endsWithChar(buffer, separatorChar)) {
      buffer.setLength(buffer.length() - 1);
    }

    return buffer.toString();
  }
}

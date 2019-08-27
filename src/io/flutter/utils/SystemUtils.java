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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SystemUtils {

  /**
   * Locate a given command-line tool given its name.
   * <p>
   * This is used to locate binaries that are not pre-installed. If it is necessary to find pre-installed
   * binaries it will require more work, especially on Windows.
   *
   * @see git4idea.config.GitExecutableDetector
   */
  @Nullable
  public static String which(String toolName) {
    File gitExecutableFromPath =
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

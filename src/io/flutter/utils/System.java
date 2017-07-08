/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

public class System {
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
        public void onTextAvailable(ProcessEvent event, Key outputType) {
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
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import org.jetbrains.annotations.NotNull;

public class XcodeUtils {

  public static boolean isSimulatorRunning() {
    final ProcessInfo[] processInfos = OSProcessUtil.getProcessList();
    for (ProcessInfo info : processInfos) {
      if (info.getExecutableName().equals("Simulator")) {
        return true;
      }
    }
    return false;
  }

  public static void startSimulator() {
    try {
      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", "Simulator.app");
      final OSProcessHandler handler = new OSProcessHandler(cmd);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull final ProcessEvent event) {
          if (event.getExitCode() != 0) {
            final String msg = event.getText() != null ? event.getText() : "Process error - exit code: (" + event.getExitCode() + ")";
            FlutterMessages.showError("Error Opening Simulator", msg);
          }
        }
      });
      handler.startNotify();
    }
    catch (ExecutionException e) {
      FlutterMessages.showError(
        "Error Opening Simulator",
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
    }
  }
}

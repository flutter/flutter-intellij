/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  public static int openSimulator(String... additionalArgs) {
    final List<String> params = new ArrayList<>(Arrays.asList(additionalArgs));
    params.add("-a");
    params.add("Simulator.app");

    try {
      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters(params);
      final ProcessOutput output = ExecUtil.execAndGetOutput(cmd);
      if (output.getExitCode() != 0) {
        final StringBuilder textBuffer = new StringBuilder();
        if (!output.getStdout().isEmpty()) {
          textBuffer.append(output.getStdout());
        }
        if (!output.getStderr().isEmpty()) {
          if (textBuffer.length() > 0) {
            textBuffer.append("\n");
          }
          textBuffer.append(output.getStderr());
        }
        
        final String eventText = textBuffer.toString();
        final String msg = !eventText.isEmpty() ? eventText : "Process error - exit code: (" + output.getExitCode() + ")";
        FlutterMessages.showError("Error Opening Simulator", msg);
      }

      return output.getExitCode();
    }
    catch (ExecutionException e) {
      FlutterMessages.showError(
        "Error Opening Simulator",
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
    }

    return 1;
  }
}

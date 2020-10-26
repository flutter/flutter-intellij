/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.utils.SystemUtils;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Open the iOS simulator.
   * <p>
   * If there's an error opening the simulator, display that to the user via
   * {@link FlutterMessages#showError(String, String, Project)}.
   */
  public static void openSimulator(@Nullable Project project, String... additionalArgs) {
    final List<String> params = new ArrayList<>(Arrays.asList(additionalArgs));
    params.add("-a");
    params.add("Simulator.app");

    final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters(params);

    SystemUtils.execAndGetOutput(cmd).thenAccept((ProcessOutput output) -> {
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
        FlutterMessages.showError("Error Opening Simulator", msg, project);
      }
    }).exceptionally(throwable -> {
      FlutterMessages.showError(
        "Error Opening Simulator",
        FlutterBundle.message("flutter.command.exception.message", throwable.getMessage()),
        project);
      return null;
    });
  }
}

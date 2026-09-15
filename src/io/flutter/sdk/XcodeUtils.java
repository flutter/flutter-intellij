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
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.utils.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class XcodeUtils {
  private static final String SIMULATOR_APP_NAME = "Simulator.app";

  public static boolean isSimulatorRunning() {
    final ProcessInfo[] processInfos = OSProcessUtil.getProcessList();
    for (ProcessInfo info : processInfos) {
      // Xcode 27 replaced Simulator.app with DeviceHub.app.
      final String name = info.getExecutableName();
      if (name.equals("Simulator") || name.equals("DeviceHub")) {
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
    CompletableFuture.supplyAsync(XcodeUtils::findSimulatorApp, AppExecutorUtil.getAppExecutorService())
      .thenCompose((String simulatorApp) -> {
        final List<String> params = new ArrayList<>(Arrays.asList(additionalArgs));
        params.add("-a");
        params.add(simulatorApp);

        final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters(params);
        return SystemUtils.execAndGetOutput(cmd);
      })
      .thenAccept((ProcessOutput output) -> {
        if (output.getExitCode() != 0) {
          final StringBuilder textBuffer = new StringBuilder();
          if (!output.getStdout().isEmpty()) {
            textBuffer.append(output.getStdout());
          }
          if (!output.getStderr().isEmpty()) {
            if (!textBuffer.isEmpty()) {
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

  /**
   * Locate the simulator app for the Xcode selected by {@code xcode-select}.
   * <p>
   * This runs a process, so it must not be called on the EDT.
   */
  @NotNull
  private static String findSimulatorApp() {
    try {
      final ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine("xcode-select", "--print-path"));
      if (output.getExitCode() == 0) {
        return getSimulatorAppPath(output.getStdout().trim());
      }
    }
    catch (ExecutionException e) {
      // Fall back to letting `open -a` resolve the app by name.
    }
    return SIMULATOR_APP_NAME;
  }

  /**
   * Return the simulator app for the given Xcode developer directory (e.g. {@code /Applications/Xcode.app/Contents/Developer}).
   * <p>
   * Xcode 27 and later ship {@code Contents/Applications/DeviceHub.app} in place of
   * {@code Contents/Developer/Applications/Simulator.app}. If neither exists, returns {@code Simulator.app}
   * so that {@code open -a} resolves the app by name.
   */
  @VisibleForTesting
  @NotNull
  static String getSimulatorAppPath(@NotNull String developerDir) {
    if (!developerDir.isEmpty()) {
      final File developer = new File(developerDir);
      final File contents = developer.getParentFile();
      if (contents != null) {
        final File deviceHub = new File(contents, "Applications/DeviceHub.app");
        if (deviceHub.isDirectory()) {
          return deviceHub.getPath();
        }
      }
      final File simulator = new File(developer, "Applications/" + SIMULATOR_APP_NAME);
      if (simulator.isDirectory()) {
        return simulator.getPath();
      }
    }
    return SIMULATOR_APP_NAME;
  }
}

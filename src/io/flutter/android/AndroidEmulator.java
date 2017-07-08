/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterMessages;

public class AndroidEmulator {
  private static final Logger LOG = Logger.getInstance(AndroidEmulator.class);

  final AndroidSdk androidSdk;
  final String id;

  AndroidEmulator(AndroidSdk androidSdk, String id) {
    this.androidSdk = androidSdk;
    this.id = id;
  }

  public String getName() {
    return id.replaceAll("_", " ");
  }

  public void startEmulator() {
    final VirtualFile emulator = androidSdk.getEmulatorToolExecutable();
    if (emulator == null) {
      FlutterMessages.showError(
        "Error Opening Emulator",
        "Unable to locate the emulator tool in the Android SDK.");
      return;
    }

    final String emulatorPath = emulator.getCanonicalPath();
    assert (emulatorPath != null);

    final GeneralCommandLine cmd = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(androidSdk.getHome().getCanonicalPath())
      .withExePath(emulatorPath)
      .withParameters("-avd", this.id);

    try {
      final StringBuilder stdout = new StringBuilder();
      final OSProcessHandler process = new OSProcessHandler(cmd);
      process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          if (outputType == ProcessOutputTypes.STDERR || outputType == ProcessOutputTypes.STDOUT) {
            stdout.append(event.getText());
          }
        }

        public void processTerminated(ProcessEvent event) {
          final int exitCode = event.getExitCode();
          if (exitCode != 0) {
            final String message = stdout.length() == 0
                                   ? "Android emulator terminated with exit code " + exitCode
                                   : stdout.toString().trim();
            FlutterMessages.showError("Error Opening Emulator", message);
          }
        }
      });
      process.startNotify();
    }
    catch (ExecutionException | RuntimeException e) {
      FlutterMessages.showError("Error Opening Emulator", e.toString());
    }
  }
}

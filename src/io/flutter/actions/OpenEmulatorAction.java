/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.android.AndroidEmulator;
import io.flutter.android.AndroidSdk;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

@SuppressWarnings("ComponentNotRegistered")
public class OpenEmulatorAction extends AnAction {
  public static List<OpenEmulatorAction> getEmulatorActions(Project project) {
    final AndroidSdk sdk = AndroidSdk.fromProject(project);
    if (sdk == null) {
      return Collections.emptyList();
    }

    final List<AndroidEmulator> emulators = sdk.getEmulators();
    return emulators.stream().map(OpenEmulatorAction::new).collect(toList());
  }

  final AndroidEmulator emulator;

  public OpenEmulatorAction(AndroidEmulator emulator) {
    super("Android: Open Emulator " + emulator.getName());

    this.emulator = emulator;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    try {
      // TODO: start the emulator, check for errors

      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", "Simulator.app");
      final OSProcessHandler handler = new OSProcessHandler(cmd);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          if (event.getExitCode() != 0) {
            FlutterMessages.showError("Error Opening Emulator", event.getText());
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

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
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;

@SuppressWarnings("ComponentNotRegistered")
public class OpenSimulatorAction extends AnAction {
  final boolean enabled;

  public OpenSimulatorAction(boolean enabled) {
    super("Open iOS Simulator");

    this.enabled = enabled;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    try {
      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", "Simulator.app");
      final OSProcessHandler handler = new OSProcessHandler(cmd);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          if (event.getExitCode() != 0) {
            FlutterMessages.showError("Error Opening Simulator", event.getText());
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

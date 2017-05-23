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
import io.flutter.FlutterMessages;


public class OpenXcodeAction extends AnAction {


  @Override
  public void actionPerformed(AnActionEvent e) {

    final Project project = e.getProject();


    final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters(myPath);
    final OSProcessHandler handler = new OSProcessHandler(cmd);
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        if (event.getExitCode() != 0) {
          FlutterMessages.showError("Error Opening ", myPath);
        }
      }
    });
    handler.startNotify();
  }
      catch (ExecutionException e) {
    FlutterMessages.showError(
      "Error Opening External File",
      "Exception: " + e.getMessage());
  }
  }
}

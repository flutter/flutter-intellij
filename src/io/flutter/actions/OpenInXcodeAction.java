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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class OpenInXcodeAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    // Enable in global menu; action group controls context menu visibility.
    e.getPresentation().setVisible(SystemInfo.isMac);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile projectFile = findProjectFile(e);
    if (projectFile != null) {
      openFile(projectFile);
    } else {
      FlutterMessages.showError("Error Opening Xcode project", "Project cannot be found");
    }
  }

  private VirtualFile findProjectFile(@Nullable AnActionEvent e) {
    if (e != null) {
      final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (file != null && file.exists() && FlutterUtils.isXcodeFileName(file.getName())) {
        return file;
      }
      final Project project = e.getProject();
      if (project != null) {
        return FlutterModuleUtils.findXcodeProjectFile(project);
      }
    }
    return null;
  }


  private static void openFile(@NotNull VirtualFile file) {
    final String path = file.getPath();
    try {
      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters(path);
      final OSProcessHandler handler = new OSProcessHandler(cmd);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          if (event.getExitCode() != 0) {
            FlutterMessages.showError("Error Opening", path);
          }
        }
      });
      handler.startNotify();
    }
    catch (ExecutionException ex) {
      FlutterMessages.showError(
        "Error Opening",
        "Exception: " + ex.getMessage());
    }

  }
}

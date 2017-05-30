/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class FlutterExternalIdeActionGroup extends DefaultActionGroup {
  @Override
  public void update(AnActionEvent e) {
    final boolean enabled = SystemInfo.isMac && isXcodeFile(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  private boolean isXcodeFile(AnActionEvent e) {
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (file == null || !file.exists()) return false;
    final Project project = e.getProject();
    return
      isProjectDirectory(file, project) || isIOsDirectory(file) || FlutterUtils.isXcodeProjectFileName(file.getName());
  }

  private boolean isIOsDirectory(@NotNull VirtualFile file) {
    return file.isDirectory() && file.getName().equals("ios");
  }

  private boolean isProjectDirectory(@NotNull VirtualFile file, @Nullable Project project) {
    return file.isDirectory() && project != null && project.getBaseDir().getPath().equals(file.getPath());
  }
}

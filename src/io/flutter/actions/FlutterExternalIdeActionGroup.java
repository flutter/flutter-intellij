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
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterExternalIdeActionGroup extends DefaultActionGroup {
  @Override
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final boolean enabled = isExternalIdeFile(event);
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  private boolean isExternalIdeFile(AnActionEvent e) {
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (file == null || !file.exists()) {
      return false;
    }

    final Project project = e.getProject();
    return
      isProjectDirectory(file, project) ||
      isIOsDirectory(file) ||
      (isAndroidDirectory(file) && !FlutterUtils.isAndroidStudio()) ||
      FlutterUtils.isXcodeProjectFileName(file.getName()) || OpenInAndroidStudioAction.isProjectFileName(file.getName());
  }

  protected static boolean isAndroidDirectory(@NotNull VirtualFile file) {
    return file.isDirectory() && file.getName().equals("android");
  }

  protected static boolean isIOsDirectory(@NotNull VirtualFile file) {
    return file.isDirectory() && file.getName().equals("ios");
  }

  private static boolean isProjectDirectory(@NotNull VirtualFile file, @Nullable Project project) {
    if (!file.isDirectory() || project == null) {
      return false;
    }

    final VirtualFile baseDir = project.getBaseDir();
    return baseDir != null && baseDir.getPath().equals(file.getPath());
  }
}

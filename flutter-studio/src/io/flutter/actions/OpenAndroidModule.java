/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BitUtil;
import io.flutter.FlutterMessages;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

/**
 * Open the selected module in Android Studio, re-using the current process
 * rather than spawning a new process (as IntelliJ does).
 */
public class OpenAndroidModule extends OpenInAndroidStudioAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile projectFile = findProjectFile(e);
    if (projectFile == null) {
      FlutterMessages.showError("Error Opening Android Studio", "Project not found.");
      return;
    }
    final int modifiers = e.getModifiers();
    // From ReopenProjectAction.
    final boolean forceOpenInNewFrame = BitUtil.isSet(modifiers, InputEvent.CTRL_MASK)
                                        || BitUtil.isSet(modifiers, InputEvent.SHIFT_MASK)
                                        || e.getPlace() == ActionPlaces.WELCOME_SCREEN;

    ProjectUtil.openOrImport(projectFile.getPath(), e.getProject(), forceOpenInNewFrame);
  }
}

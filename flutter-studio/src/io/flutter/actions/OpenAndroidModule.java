/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BitUtil;
import io.flutter.FlutterMessages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.awt.event.InputEvent;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.intellij.ide.impl.ProjectUtil.*;

/**
 * Open the selected module in Android Studio, re-using the current process
 * rather than spawning a new process (as IntelliJ does).
 */
public class OpenAndroidModule extends OpenInAndroidStudioAction implements DumbAware {
  private static void openOrImportProject(@NotNull VirtualFile file, @Nullable Project project, boolean forceOpenInNewFrame) {
    // This is very similar to AndroidOpenFileAction.openOrImportProject().
    if (canImportAsGradleProject(file)) {
      VirtualFile target = findImportTarget(file);
      if (target != null) {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length > 0) {
          int exitCode = forceOpenInNewFrame ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW : confirmOpenNewProject(false);
          if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
            Project toClose = ((project != null) && !project.isDefault()) ? project : openProjects[openProjects.length - 1];
            if (!closeAndDispose(toClose)) {
              return;
            }
          }
          else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
            return;
          }
        }

        GradleProjectImporter gradleImporter = GradleProjectImporter.getInstance();
        gradleImporter.importProject(file);
        return;
      }
    }
    openOrImport(file.getPath(), project, false);
  }

  public static boolean canImportAsGradleProject(@NotNull VirtualFile importSource) {
    VirtualFile target = findImportTarget(importSource);
    return target != null && GradleConstants.EXTENSION.equals(target.getExtension());
  }

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

    // Using:
    //ProjectUtil.openOrImport(projectFile.getPath(), e.getProject(), forceOpenInNewFrame);
    // presents the user with a really imposing Gradle project import dialog.
    openOrImportProject(projectFile, e.getProject(), forceOpenInNewFrame);
  }
}

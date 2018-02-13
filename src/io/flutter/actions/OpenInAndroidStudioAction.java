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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class OpenInAndroidStudioAction extends AnAction {

  private static void openFileInStudio(@NotNull VirtualFile file, @NotNull String androidStudioPath) {
    try {
      final GeneralCommandLine cmd;
      if (SystemInfo.isMac) {
        cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", androidStudioPath, file.getPath());
      }
      else {
        cmd = new GeneralCommandLine().withExePath(androidStudioPath).withParameters(file.getPath());
      }
      final OSProcessHandler handler = new OSProcessHandler(cmd);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          if (event.getExitCode() != 0) {
            FlutterMessages.showError("Error Opening", file.getPath());
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

  protected static boolean isProjectFileName(String name) {
    return name.endsWith("_android.iml");
  }

  @Nullable
  private static VirtualFile findStudioProjectFile(@NotNull Project project) {
    for (PubRoot root : PubRoots.forProject(project)) {
      for (VirtualFile child : root.getRoot().getChildren()) {
        if (isProjectFileName(child.getName())) {
          return child;
        }
      }
    }

    return null;
  }

  @Nullable
  private static String findAndroidStudio(@Nullable Project project) {
    if (project == null) {
      return null;
    }

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk != null) {
      String androidSdkLocation = flutterSdk.queryFlutterConfig("android-studio-dir", true);
      if (androidSdkLocation != null) {
        if (androidSdkLocation.contains("/Android Studio 2")) {
          Messages.showErrorDialog(FlutterBundle.message("old.android.studio.message", File.separator),
                                   FlutterBundle.message("old.android.studio.title"));
          return null;
        }
        if (androidSdkLocation.endsWith("/")) {
          androidSdkLocation = androidSdkLocation.substring(0, androidSdkLocation.length() - 1);
        }
        final String contents = "/Contents";
        // On a mac, trim off "/Contents".
        if (SystemInfo.isMac && androidSdkLocation.endsWith(contents)) {
          return androidSdkLocation.substring(0, androidSdkLocation.length() - contents.length());
        }
        return androidSdkLocation;
      }
    }
    return null;
  }

  private static VirtualFile findProjectFile(@Nullable AnActionEvent e) {
    if (e != null) {
      final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (file != null && file.exists()) {
        if (isProjectFileName(file.getName())) {
          return file;
        }

        final Project project = e.getProject();
        assert (project != null);
        // Return null if this is an ios folder.
        if (FlutterExternalIdeActionGroup.isWithinIOsDirectory(file, project)) {
          return null;
        }
      }

      final Project project = e.getProject();
      if (project != null) {
        return findStudioProjectFile(project);
      }
    }
    return null;
  }

  @Override
  public void update(AnActionEvent event) {
    final boolean enabled = findProjectFile(event) != null;

    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final String androidStudioPath = findAndroidStudio(e.getProject());
    if (androidStudioPath == null) {
      FlutterMessages.showError("Error Opening Android Studio", "Unable to locate Android Studio.");
      return;
    }

    final VirtualFile projectFile = findProjectFile(e);
    if (projectFile == null) {
      FlutterMessages.showError("Error Opening Android Studio", "Project not found.");
      return;
    }

    openFileInStudio(projectFile, androidStudioPath);
  }
}

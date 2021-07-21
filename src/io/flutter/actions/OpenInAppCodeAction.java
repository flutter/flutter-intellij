/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterMessages;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.flutter.actions.OpenInXcodeAction.findProjectFile;

public class OpenInAppCodeAction extends AnAction {

  private static boolean IS_INITIALIZED = false;
  private static boolean IS_APPCODE_INSTALLED = false;

  static {
    initialize();
  }

  private static void initialize() {
    if (SystemInfo.isMac) {
      try {
        // AppCode is installed if this shell command produces any output:
        // mdfind "kMDItemContentType == 'com.apple.application-bundle'" | grep AppCode.app
        final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("/bin/bash")
          .withParameters("-c", "mdfind \"kMDItemContentType == 'com.apple.application-bundle'\" | grep AppCode.app");
        final ColoredProcessHandler handler = new ColoredProcessHandler(cmd);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (outputType == ProcessOutputTypes.STDOUT) {
              IS_APPCODE_INSTALLED = true;
            }
          }
        });
        handler.startNotify();
      }
      catch (ExecutionException ex) {
        // ignored
      }
    }
    IS_INITIALIZED = true;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    // Enable in global menu; action group controls context menu visibility.
    if (!SystemInfo.isMac || !(IS_INITIALIZED && IS_APPCODE_INSTALLED)) {
      event.getPresentation().setVisible(false);
    }
    else {
      final Presentation presentation = event.getPresentation();
      final boolean enabled = findProjectFile(event) != null;
      presentation.setEnabledAndVisible(enabled);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final VirtualFile projectFile = findProjectFile(event);
    if (projectFile != null) {
      openFile(projectFile);
    }
    else {
      @Nullable final Project project = event.getProject();
      FlutterMessages.showError("Error Opening AppCode", "Project not found.", project);
    }
  }

  private static void openFile(@NotNull VirtualFile file) {
    final Project project = ProjectUtil.guessProjectForFile(file);
    final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;
    if (sdk == null) {
      FlutterSdkAction.showMissingSdkDialog(project);
      return;
    }
    openInAppCode(project, file.getParent().getPath());
  }

  private static void openInAppCode(@Nullable Project project, @NotNull String path) {
    try {
      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", "AppCode.app", path);
      final ColoredProcessHandler handler = new ColoredProcessHandler(cmd);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull final ProcessEvent event) {
          if (event.getExitCode() != 0) {
            FlutterMessages.showError("Error Opening", path, project);
          }
        }
      });
      handler.startNotify();
    }
    catch (ExecutionException ex) {
      FlutterMessages.showError(
        "Error Opening",
        "Exception: " + ex.getMessage(),
        project);
    }
  }
}

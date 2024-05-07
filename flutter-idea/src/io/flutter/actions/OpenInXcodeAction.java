/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.ProgressHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenInXcodeAction extends AnAction {

  @Nullable
  static VirtualFile findProjectFile(@NotNull AnActionEvent event) {
    final VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    final Project project = event.getProject();
    if (file != null && file.exists()) {
      if (FlutterUtils.isXcodeFileName(file.getName())) {
        return file;
      }

      if (project == null) {
        return null;
      }

      // Return null if this is an android folder.
      if (FlutterExternalIdeActionGroup.isWithinAndroidDirectory(file, project) ||
          OpenInAndroidStudioAction.isProjectFileName(file.getName())) {
        return null;
      }
    }

    if (project != null) {
      return FlutterModuleUtils.findXcodeProjectFile(project, file);
    }

    return null;
  }

  private static void openFile(@NotNull VirtualFile file) {
    final Project project = ProjectUtil.guessProjectForFile(file);
    final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;
    if (sdk == null) {
      FlutterSdkAction.showMissingSdkDialog(project);
      return;
    }

    final PubRoot pubRoot = PubRoot.forFile(file);
    if (pubRoot == null) {
      FlutterMessages.showError("Error Opening Xcode", "Unable to run `flutter build` (no pub root found)", project);
      return;
    }

    // Trigger an iOS build if necessary.
    if (!hasBeenBuilt(pubRoot, sdk)) {
      final ProgressHelper progressHelper = new ProgressHelper(project);
      progressHelper.start("Building for iOS");

      String buildArg = "--config-only";
      if (!sdk.getVersion().isXcodeConfigOnlySupported()) {
        buildArg = "--simulator";
      }
      // TODO(pq): consider a popup explaining why we're doing a build.
      // Note: we build only for the simulator to bypass device provisioning issues.
      final ColoredProcessHandler processHandler = sdk.flutterBuild(pubRoot, "ios", buildArg).startInConsole(project);
      if (processHandler == null) {
        progressHelper.done();
        FlutterMessages.showError("Error Opening Xcode", "unable to run `flutter build`", project);
      }
      else {
        processHandler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            progressHelper.done();

            final int exitCode = event.getExitCode();
            if (exitCode != 0) {
              FlutterMessages.showError("Error Opening Xcode", "`flutter build` returned: " + exitCode, project);
              return;
            }

            openWithXcode(project, file.getPath());
          }
        });
      }
    }
    else {
      openWithXcode(project, file.getPath());
    }
  }

  private static boolean hasBeenBuilt(@NotNull PubRoot pubRoot, @NotNull FlutterSdk sdk) {
    if (sdk.getVersion().isXcodeConfigOnlySupported()) {
      // Derived from packages/flutter_tools/test/integration.shard/build_ios_config_only_test.dart
      final VirtualFile ios = pubRoot.getRoot().findChild("ios");
      if (ios == null || !ios.isDirectory()) return false;
      final VirtualFile flutter = ios.findChild("Flutter");
      if (flutter == null || !flutter.isDirectory()) return false;
      final VirtualFile gen = flutter.findChild("Generated.xcconfig");
      if (gen == null || gen.isDirectory()) return false;
      return sdk.isOlderThanToolsStamp(gen);
    } else {
      final VirtualFile buildDir = pubRoot.getRoot().findChild("build");
      return buildDir != null && buildDir.isDirectory() && buildDir.findChild("ios") != null;
    }
  }

  private static void openWithXcode(@Nullable Project project, String path) {
    try {
      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters(path);
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

  @Override
  public void update(@NotNull AnActionEvent event) {
    // Enable in global menu; action group controls context menu visibility.
    if (!SystemInfo.isMac) {
      event.getPresentation().setVisible(false);
    }
    else {
      final Presentation presentation = event.getPresentation();
      final boolean enabled = findProjectFile(event) != null;
      presentation.setEnabledAndVisible(enabled);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final VirtualFile projectFile = findProjectFile(event);
    if (projectFile != null) {
      openFile(projectFile);
    }
    else {
      @Nullable final Project project = event.getProject();

      FlutterMessages.showError("Error Opening Xcode", "Project not found.", project);
    }
  }
}

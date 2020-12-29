/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.console.FlutterConsoles;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterDoctorAction extends FlutterSdkAction {

  private static final Logger LOG = Logger.getInstance(FlutterDoctorAction.class);

  public void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root) {
    sdk.flutterDoctor().startInConsole(project);
  }

  @Override
  public void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root, @NotNull DataContext context) {
    startCommand(project, sdk, root);
  }

  @Override
  public void startCommandInBazelContext(@NotNull Project project, @NotNull Workspace workspace) {
    final String doctorScript = workspace.getDoctorScript();
    if (doctorScript != null) {
      runWorkspaceFlutterDoctorScript(project, workspace.getRoot().getPath(), doctorScript);
    }
    else {
      FlutterUtils.warn(LOG, "No \"doctorScript\" script in the flutter.json file.");
    }
  }

  private void runWorkspaceFlutterDoctorScript(@NotNull Project project, @NotNull String workDir, @NotNull String doctorScript) {
    final GeneralCommandLine cmdLine = new GeneralCommandLine().withWorkDirectory(workDir);
    cmdLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    cmdLine.setExePath(FileUtil.toSystemDependentName(doctorScript));

    final ColoredProcessHandler handler;
    try {
      handler = new ColoredProcessHandler(cmdLine);
      FlutterConsoles.displayProcessLater(handler, project, null, handler::startNotify);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean enableActionInBazelContext() {
    return true;
  }
}

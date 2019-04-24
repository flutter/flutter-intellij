/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This subclasses FlutterCommand specifically to override the command line creation behavior for
 * Flutter Web run commands.
 */
public class FlutterWebCommand extends FlutterCommand {
  public FlutterWebCommand(@NotNull FlutterSdk sdk, @Nullable VirtualFile workDir, @NotNull FlutterCommand.Type type, String... args) {
    super(sdk, workDir, type, args);
  }

  @NotNull
  public GeneralCommandLine createGeneralCommandLine(@Nullable Project project) {
    final GeneralCommandLine line = new GeneralCommandLine();
    line.setCharset(CharsetToolkit.UTF8_CHARSET);
    if (workDir != null) {
      line.setWorkDirectory(workDir.getPath());
    }
    line.setExePath(FileUtil.toSystemDependentName(sdk.getHomePath() + "/bin/" + FlutterSdkUtil.flutterScriptName()));
    // flutter packages pub
    line.addParameters(Type.PACKAGES_PUB.subCommand);
    line.addParameters("global", "run", "webdev", "daemon");
    line.addParameters(args);
    return line;
  }
}

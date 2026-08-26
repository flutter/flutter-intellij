/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterUpgradeAction extends FlutterSdkAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (SystemInfo.isWindows && project != null && FlutterSdk.getFlutterSdk(project) != null) {
      FlutterMessages.showDialog(project,
                                 FlutterBundle.message("flutter.upgrade.windows.message"),
                                 FlutterBundle.message("flutter.upgrade.windows.title"),
                                 new String[]{"OK"}, 0);
      return;
    }

    super.actionPerformed(event);
  }

  @Override
  public void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root, @NotNull DataContext context) {
    sdk.flutterUpgrade().startInConsole(project);
  }
}

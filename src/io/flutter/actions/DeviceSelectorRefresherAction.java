/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.editor.FlutterMaterialIcons;
import io.flutter.run.daemon.DeviceService;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

public class DeviceSelectorRefresherAction extends AnAction {
  public DeviceSelectorRefresherAction() {
    super(FlutterMaterialIcons.getIconForName("refresh"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project != null) {
      DeviceService.getInstance(project).restart();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(FlutterModuleUtils.hasInternalDartSdkPath(e.getProject()));
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterInitializer;
import io.flutter.run.daemon.DeviceService;

public class RestartFlutterDaemonAction extends AnAction {
  public RestartFlutterDaemonAction() {
    super("Refresh");
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    FlutterInitializer.sendAnalyticsAction(this);

    final Project project = event.getProject();
    if (project == null) {
      return;
    }

    DeviceService.getInstance(project).restart();
  }
}

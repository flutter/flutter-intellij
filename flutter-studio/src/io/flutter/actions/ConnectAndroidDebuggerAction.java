/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.android.actions.AndroidConnectDebuggerAction;

public class ConnectAndroidDebuggerAction extends AndroidConnectDebuggerAction {

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDefault()) {
      super.update(e);
      return;
    }
    if (FlutterSdkUtil.hasFlutterModules(project)) {
      // Hide this button in Flutter projects; defer to superclass for Android projects.
      e.getPresentation().setVisible(false);
      return;
    }
    super.update(e);
  }
}

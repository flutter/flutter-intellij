/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.Nullable;

public class FlutterToolsActionGroup extends DefaultActionGroup {

  @Override
  public void update(@Nullable AnActionEvent e) {
    final Project project = e == null ? null : e.getProject();
    final Presentation presentation = e == null ? null : e.getPresentation();
    if (presentation != null) {
      final boolean visible = project == null || !FlutterModuleUtils.usesFlutter(project);
      presentation.setEnabled(visible);
      presentation.setVisible(visible);
    }
  }
}

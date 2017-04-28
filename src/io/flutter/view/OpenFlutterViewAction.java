/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class OpenFlutterViewAction extends DumbAwareAction {
  private final Computable<Boolean> myIsApplicable;

  public OpenFlutterViewAction(@NotNull final Computable<Boolean> isApplicable) {
    super("Open Flutter View", "Open Flutter View", FlutterIcons.Flutter_inspect);

    myIsApplicable = isApplicable;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(myIsApplicable.compute());
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    FlutterInitializer.sendAnalyticsAction(this);

    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Flutter");
    toolWindow.show(null);
  }
}

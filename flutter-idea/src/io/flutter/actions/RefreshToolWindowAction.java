/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class RefreshToolWindowAction extends DumbAwareAction {
  private String toolWindowId;

  public RefreshToolWindowAction() {
    super(AllIcons.Actions.Refresh);
  }

  public RefreshToolWindowAction(@NotNull String toolWindowId) {
    super(AllIcons.Actions.Refresh);
    this.toolWindowId = toolWindowId;
  }
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project == null) {
      return;
    }

    Optional.ofNullable(
        FlutterUtils.embeddedBrowser(project))
      .ifPresent(embeddedBrowser -> embeddedBrowser.refresh(toolWindowId));
  }
}

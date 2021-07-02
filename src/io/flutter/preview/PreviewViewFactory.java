/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class PreviewViewFactory implements ToolWindowFactory, DumbAware {
  public static void init(@NotNull Project project) {
    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(PreviewView.TOOL_WINDOW_ID);
    if (window != null) {
      window.setAvailable(true);
    }
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    //noinspection CodeBlock2Expr
    DumbService.getInstance(project).runWhenSmart(() -> {
      (ServiceManager.getService(project, PreviewView.class)).initToolWindow(toolWindow);
    });
  }

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return false;
  }
}

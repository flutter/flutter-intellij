/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

// TODO: toolbar, reload status, connection status, fps?

public class FlutterViewFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    DumbService.getInstance(project).runWhenSmart(() -> {
      // TODO:

      //((TodoView)ServiceManager.getService(project, TodoView.class)).initToolWindow(toolWindow);
    });
  }
}

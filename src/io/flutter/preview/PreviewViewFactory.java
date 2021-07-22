/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import io.flutter.performance.FlutterPerformanceView;
import io.flutter.utils.ViewListener;
import org.jetbrains.annotations.NotNull;

public class PreviewViewFactory implements ToolWindowFactory, DumbAware {
  private static final String TOOL_WINDOW_VISIBLE_PROPERTY = "flutter.preview.tool.window.visible";

  public static void init(@NotNull Project project) {
    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(PreviewView.TOOL_WINDOW_ID);
    if (window != null) {
      window.setAvailable(true);

      if (PropertiesComponent.getInstance(project).getBoolean(TOOL_WINDOW_VISIBLE_PROPERTY, false)) {
        window.activate(null, false);
      }
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

  public static class PreviewViewListener extends ViewListener {
    public PreviewViewListener(@NotNull Project project) {
      super(project, PreviewView.TOOL_WINDOW_ID, TOOL_WINDOW_VISIBLE_PROPERTY);
    }
  }
}

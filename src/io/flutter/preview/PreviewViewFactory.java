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

  /**
   * Helps to initialize tool window with the same visibility state as it was when the project was previously closed.
   */
  public static class PreviewViewListener implements ToolWindowManagerListener {
    private final @NotNull Project myProject;

    public PreviewViewListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      ToolWindow toolWindow = toolWindowManager.getToolWindow(PreviewView.TOOL_WINDOW_ID);
      if (toolWindow != null && toolWindow.isAvailable()) {
        PropertiesComponent.getInstance(myProject).setValue(TOOL_WINDOW_VISIBLE_PROPERTY, toolWindow.isVisible(), false);
      }
    }
  }
}

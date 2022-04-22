/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import io.flutter.utils.ViewListener;
import org.jetbrains.annotations.NotNull;

public class FlutterViewFactory implements ToolWindowFactory, DumbAware {
  private static final String TOOL_WINDOW_VISIBLE_PROPERTY = "flutter.view.tool.window.visible";

  public static void init(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (event) -> initFlutterView(project, event)
    );

    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(FlutterView.TOOL_WINDOW_ID);
    if (window != null) {
      window.setAvailable(true);

      if (PropertiesComponent.getInstance(project).getBoolean(TOOL_WINDOW_VISIBLE_PROPERTY, false)) {
        window.activate(null, false);
      }
    }
  }

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return false;
  }

  private static void initFlutterView(@NotNull Project project, FlutterViewMessages.FlutterDebugEvent event) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final FlutterView flutterView = project.getService(FlutterView.class);
      flutterView.debugActive(event);
    });
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    //noinspection CodeBlock2Expr
    DumbService.getInstance(project).runWhenSmart(() -> {
      project.getService(FlutterView.class).initToolWindow(toolWindow);
    });
  }

  public static class FlutterViewListener extends ViewListener {
    public FlutterViewListener(@NotNull Project project) {
      super(project, FlutterView.TOOL_WINDOW_ID, TOOL_WINDOW_VISIBLE_PROPERTY);
    }
  }
}

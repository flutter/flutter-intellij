/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.performance;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import io.flutter.utils.ViewListener;
import io.flutter.view.FlutterViewMessages;
import org.jetbrains.annotations.NotNull;

public class FlutterPerformanceViewFactory implements ToolWindowFactory, DumbAware {
  private static final String TOOL_WINDOW_VISIBLE_PROPERTY = "flutter.performance.tool.window.visible";

  public static void init(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (event) -> initPerfView(project, event)
    );
    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(FlutterPerformanceView.TOOL_WINDOW_ID);
    if (window != null) {
      window.setAvailable(true);

      if (PropertiesComponent.getInstance(project).getBoolean(TOOL_WINDOW_VISIBLE_PROPERTY, false)) {
        window.activate(null, false);
      }
    }
  }

  private static void initPerfView(@NotNull Project project, FlutterViewMessages.FlutterDebugEvent event) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final FlutterPerformanceView flutterPerfView = ServiceManager.getService(project, FlutterPerformanceView.class);
      ToolWindowManager.getInstance(project).getToolWindow(FlutterPerformanceView.TOOL_WINDOW_ID).setAvailable(true);
      flutterPerfView.debugActive(event);
    });
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    //noinspection CodeBlock2Expr
    DumbService.getInstance(project).runWhenSmart(() -> {
      (ServiceManager.getService(project, FlutterPerformanceView.class)).initToolWindow(toolWindow);
    });
  }

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return false;
  }

  public static class FlutterPerformanceViewListener extends ViewListener {
    public FlutterPerformanceViewListener(@NotNull Project project) {
      super(project, FlutterPerformanceView.TOOL_WINDOW_ID, TOOL_WINDOW_VISIBLE_PROPERTY);
    }
  }
}

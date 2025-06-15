/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;

public class InspectorViewFactory implements ToolWindowFactory, DumbAware {
  private static final String TOOL_WINDOW_VISIBLE_PROPERTY = "flutter.view.tool.window.visible";

  public static void init(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (FlutterViewMessages.FlutterDebugNotifier)(event) -> initInspectorView(project, event)
    );

    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(InspectorView.TOOL_WINDOW_ID);
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

  private static void initInspectorView(@NotNull Project project, FlutterViewMessages.FlutterDebugEvent event) {
    OpenApiUtils.safeInvokeLater(() -> {
      final InspectorView inspectorView = project.getService(InspectorView.class);
      inspectorView.debugActive(event);
    });
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    //noinspection CodeBlock2Expr
    DumbService.getInstance(project).runWhenSmart(() -> {
      project.getService(InspectorView.class).initToolWindow(toolWindow);
    });
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class FlutterViewFactory implements ToolWindowFactory {
  public static void init(@NotNull Project project) {
    //noinspection CodeBlock2Expr
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (event) -> {
        initFlutterView(project, event);
      }
    );
  }

  private static void initFlutterView(@NotNull Project project, FlutterViewMessages.FlutterDebugEvent event) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final FlutterView flutterView = ServiceManager.getService(project, FlutterView.class);
      flutterView.debugActive(event);
    });
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    (ServiceManager.getService(project, FlutterView.class)).initToolWindow(toolWindow);
  }
}

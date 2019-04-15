/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class FlutterPerfViewFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void init(ToolWindow window) {
    window.setAvailable(false, null);
  }

  public static void init(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (event) -> initPerfView(project, event)
    );
  }

  private static void initPerfView(@NotNull Project project, FlutterViewMessages.FlutterDebugEvent event) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final FlutterPerfView flutterPerfView = ServiceManager.getService(project, FlutterPerfView.class);
      flutterPerfView.debugActive(event);
    });
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    //noinspection CodeBlock2Expr
    DumbService.getInstance(project).runWhenSmart(() -> {
      (ServiceManager.getService(project, FlutterPerfView.class)).initToolWindow(toolWindow);
    });
  }
}

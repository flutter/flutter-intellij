/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceWrapper;
import org.dartlang.vm.service.VmService;
import org.jetbrains.annotations.NotNull;

public class FlutterViewFactory implements ToolWindowFactory {
  public static void init(@NotNull Project project) {
    //noinspection CodeBlock2Expr
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (observatoryConnector, vmServiceWrapper, vmService) -> {
        openFlutterView(project, observatoryConnector, vmServiceWrapper, vmService);
      });
  }

  private static void openFlutterView(Project project, ObservatoryConnector observatoryConnector, VmServiceWrapper vmServiceWrapper, VmService vmService) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Flutter");
      toolWindow.show(() -> {
        final FlutterView flutterView = ServiceManager.getService(project, FlutterView.class);
        flutterView.debugActive(vmServiceWrapper, vmService);
      });
    });
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    //noinspection CodeBlock2Expr
    DumbService.getInstance(project).runWhenSmart(() -> {
      (ServiceManager.getService(project, FlutterView.class)).initToolWindow(toolWindow);
    });
  }
}

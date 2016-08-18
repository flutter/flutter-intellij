/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;

public class FlutterConsole {

  private static ConsoleView console;

  /**
   * Attach the flutter console to the process managed by the given processHandler.
   */
  public static void attach(@NotNull Project project, @NotNull OSProcessHandler processHandler, @NotNull String processTitle) {
    final ConsoleView console = getConsole(project);
    console.attachToProcess(processHandler);
    show(project, console, processTitle);
  }

  private static void show(@NotNull Project project, @NotNull ConsoleView console, String processTitle) {
    final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(false, true);
    toolWindowPanel.setContent(console.getComponent());

    final Content content = ContentFactory.SERVICE.getInstance().createContent(toolWindowPanel.getComponent(), processTitle, true);
    Disposer.register(content, console);

    final MessageView messageView = MessageView.SERVICE.getInstance(project);
    messageView.runWhenInitialized(() -> {
      final ContentManager contentManager = messageView.getContentManager();
      contentManager.addContent(content);
      contentManager.setSelectedContent(content);

      final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
      toolWindow.activate(null, true);
    });
  }

  private static ConsoleView getConsole(@NotNull Project project) {
    if (console == null) {
      final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
      consoleBuilder.setViewer(true);
      console = consoleBuilder.getConsole();
    }
    return console;
  }
}

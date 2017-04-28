/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.module.Module;
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
import org.jetbrains.annotations.Nullable;

/**
 * A console showing output from a Flutter command.
 */
class FlutterConsole {
  @NotNull final ConsoleView view;
  @NotNull final Content content;
  @NotNull final Project project;
  @Nullable final Module module;

  @Nullable
  private Runnable cancelProcessSubscription;

  private FlutterConsole(@NotNull ConsoleView view, @NotNull Content content, @NotNull Project project, @Nullable Module module) {
    this.view = view;
    this.content = content;
    this.project = project;
    this.module = module;
  }

  @NotNull
  static FlutterConsole create(@NotNull Project project, @Nullable Module module) {
    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    builder.setViewer(true);
    if (module != null) {
      builder.addFilter(new FlutterConsoleFilter(module));
    }
    final ConsoleView view = builder.getConsole();

    final SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
    panel.setContent(view.getComponent());

    final String title = module != null ? "[" + module.getName() + "] Flutter" : "Flutter";
    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel.getComponent(), title, true);
    Disposer.register(content, view);

    return new FlutterConsole(view, content, project, module);
  }

  /**
   * Starts displaying the output of a different process.
   */
  void watchProcess(OSProcessHandler process) {
    if (cancelProcessSubscription != null) {
      cancelProcessSubscription.run();
      cancelProcessSubscription = null;
    }

    view.clear();
    view.attachToProcess(process);
    process.startNotify();

    // Print exit code.
    final ProcessAdapter listener = new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        view.print(
          "Process finished with exit code " + event.getExitCode(),
          ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    };
    process.addProcessListener(listener);
    cancelProcessSubscription = () -> process.removeProcessListener(listener);
  }

  /**
   * Moves this console to the end of the tool window's tab list, selects it, and shows the tool window.
   */
  void bringToFront() {
    // Move the tab to be last and select it.
    final MessageView messageView = MessageView.SERVICE.getInstance(project);
    final ContentManager contentManager = messageView.getContentManager();
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    // Show the panel.
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
    toolWindow.activate(null, true);
  }
}

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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterConsoleHelper {
  private static final Key<FlutterConsoleInfo> FLUTTER_MESSAGES_KEY = Key.create("FLUTTER_MESSAGES_KEY");

  /**
   * Attach the flutter console to the process managed by the given processHandler.
   */
  public static void attach(@NotNull Project project, @NotNull OSProcessHandler processHandler) {
    show(project, null, processHandler);
  }

  /**
   * Attach the flutter console to the process managed by the given processHandler.
   */
  public static void attach(@NotNull Module module, @NotNull OSProcessHandler processHandler) {
    show(module.getProject(), module, processHandler);
  }

  /**
   * We share one Flutter console per Module, and have one global Flutter console (for tasks which don't
   * take a module, like 'flutter doctor'). We could revisit to have one global, shared flutter console
   * instance.
   */
  private static void show(@NotNull Project project,
                           @Nullable Module module,
                           @NotNull OSProcessHandler processHandler) {
    final MessageView messageView = MessageView.SERVICE.getInstance(project);

    messageView.runWhenInitialized(() -> {
      final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
      final ContentManager contentManager = messageView.getContentManager();
      FlutterConsoleInfo info = findExistingInfoForCommand(project, module);

      if (info != null) {
        info.console.clear();
        contentManager.setSelectedContent(info.content);

        toolWindow.activate(null, true);
        info.console.attachToProcess(processHandler);
      }
      else {
        final ConsoleView console = createConsole(project, module);
        info = new FlutterConsoleInfo(console, module);

        final String title = module != null ? "[" + module.getName() + "] Flutter" : "Flutter";
        final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(
          false, true);
        toolWindowPanel.setContent(info.console.getComponent());

        info.content = ContentFactory.SERVICE.getInstance().createContent(
          toolWindowPanel.getComponent(), title, true);
        info.content.putUserData(FLUTTER_MESSAGES_KEY, info);
        Disposer.register(info.content, info.console);

        contentManager.addContent(info.content);
        contentManager.setSelectedContent(info.content);

        toolWindow.activate(null, true);
        info.console.attachToProcess(processHandler);
      }
    });
  }

  private static ConsoleView createConsole(@NotNull Project project, @Nullable Module module) {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    consoleBuilder.setViewer(true);
    if (module != null) {
      consoleBuilder.addFilter(new FlutterConsoleFilter(module));
    }
    return consoleBuilder.getConsole();
  }

  @Nullable
  private static FlutterConsoleInfo findExistingInfoForCommand(@NotNull Project project, @Nullable Module module) {
    for (Content content : MessageView.SERVICE.getInstance(project).getContentManager().getContents()) {
      final FlutterConsoleInfo info = content.getUserData(FLUTTER_MESSAGES_KEY);
      if (info != null && info.module == module) {
        return info;
      }
    }
    return null;
  }

  @Nullable
  public static ConsoleView findConsoleView(@Nullable Project project, @Nullable Module module) {
    if (project == null && module != null) {
      project = module.getProject();
    }
    if (project != null) {
      final FlutterConsoleInfo info = findExistingInfoForCommand(project, module);
      if (info != null) {
        return info.console;
      }
    }
    return null;
  }


  static class FlutterConsoleInfo {
    @NotNull final ConsoleView console;
    @Nullable final Module module;
    Content content;

    FlutterConsoleInfo(@NotNull ConsoleView console, @Nullable Module module) {
      this.console = console;
      this.module = module;
    }
  }
}

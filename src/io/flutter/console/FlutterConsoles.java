/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Methods that use the appropriate Flutter console.
 * <p>
 * We share one Flutter console per Module, and have one global Flutter console (for tasks which don't
 * take a module, like 'flutter doctor').
 */
public class FlutterConsoles {
  private FlutterConsoles() {
  }

  /**
   * Shows a process's output on the appropriate console. (Asynchronous.)
   *
   * @param module if not null, show in this module's console.
   */
  public static void displayProcessLater(@NotNull ColoredProcessHandler process,
                                         @NotNull Project project,
                                         @Nullable Module module,
                                         @NotNull Runnable onReady) {

    // Getting a MessageView has to happen on the UI thread.
    OpenApiUtils.safeInvokeLater(() -> {
      final MessageView messageView = MessageView.getInstance(project);
      messageView.runWhenInitialized(() -> {
        final FlutterConsole console = findOrCreate(project, module);
        console.watchProcess(process);
        console.bringToFront();
        onReady.run();
      });
    });
  }

  public static void displayMessage(@NotNull Project project, @Nullable Module module, @NotNull String message) {
    displayMessage(project, module, message, false);
  }

  public static void displayMessage(@NotNull Project project, @Nullable Module module, @NotNull String message, boolean clearContent) {
    // Getting a MessageView has to happen on the UI thread.
    OpenApiUtils.safeInvokeLater(() -> {
      final MessageView messageView = MessageView.getInstance(project);
      messageView.runWhenInitialized(() -> {
        final FlutterConsole console = findOrCreate(project, module);
        if (clearContent) {
          console.view.clear();
        }
        console.view.print(message, ConsoleViewContentType.NORMAL_OUTPUT);
        console.bringToFront();
      });
    });
  }

  @NotNull
  static FlutterConsole findOrCreate(@NotNull Project project, @Nullable Module module) {
    for (Content content : MessageView.getInstance(project).getContentManager().getContents()) {
      if (content == null) continue;
      final FlutterConsole console = content.getUserData(KEY);
      if (console != null && console.module == module) {
        assert (project == console.project);
        return console;
      }
    }
    final FlutterConsole console = FlutterConsole.create(project, module);
    console.content.putUserData(FlutterConsoles.KEY, console);
    return console;
  }

  static final Key<FlutterConsole> KEY = Key.create("FLUTTER_CONSOLE_KEY");
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
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
  public static void displayProcess(@NotNull OSProcessHandler process,
                                    @NotNull Project project,
                                    @Nullable Module module) {

    // Getting a MessageView has to happen on the UI thread.
    ApplicationManager.getApplication().invokeLater(() -> {
      final MessageView messageView = MessageView.SERVICE.getInstance(project);
      messageView.runWhenInitialized(() -> {
        FlutterConsole console = find(project, module);
        if (console == null) {
          console = FlutterConsole.create(project, module);
          console.content.putUserData(FlutterConsoles.KEY, console);
        }
        console.watchProcess(process);
        console.bringToFront();
      });
    });
  }

  @Nullable
  static FlutterConsole find(@NotNull Project project, @Nullable Module module) {
    for (Content content : MessageView.SERVICE.getInstance(project).getContentManager().getContents()) {
      final FlutterConsole console = content.getUserData(KEY);
      if (console != null && console.module == module) {
        assert (project == console.project);
        return console;
      }
    }
    return null;
  }

  static final Key<FlutterConsole> KEY = Key.create("FLUTTER_CONSOLE_KEY");
}

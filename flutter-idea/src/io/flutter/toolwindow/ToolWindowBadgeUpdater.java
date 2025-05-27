/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.BadgeIcon;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;

import java.awt.Color;
import java.awt.Paint;
import java.util.Objects;
import javax.swing.Icon;

public class ToolWindowBadgeUpdater {
  public static final Paint BADGE_PAINT = Color.decode("#5ca963");

  /**
   * Updates the tool window icons for RUN or DEBUG mode with a green badge.
   *
   * @param app The FlutterApp instance running in a given mode.
   * @param project The current IntelliJ project context.
   */
  public static void updateBadgedIcon(FlutterApp app, Project project) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(Objects.requireNonNull(project));
    final ToolWindow runToolWindow = manager.getToolWindow(ToolWindowId.RUN);
    final ToolWindow debugToolWindow = manager.getToolWindow(ToolWindowId.DEBUG);

    if(Objects.requireNonNull(app).getMode() == RunMode.RUN) {
      if (runToolWindow != null) {
        manager.invokeLater(() -> {
          Icon baseIcon = AllIcons.Toolwindows.ToolWindowRun;
          BadgeIcon iconWithBadge = new BadgeIcon(baseIcon, BADGE_PAINT);

          runToolWindow.setIcon(iconWithBadge);
        });
      }
    }
    else if(app.getMode() == RunMode.DEBUG) {
      manager.invokeLater(() -> {
        Icon baseIcon = AllIcons.Toolwindows.ToolWindowDebugger;
        BadgeIcon iconWithBadge = new BadgeIcon(baseIcon, BADGE_PAINT);

        Objects.requireNonNull(debugToolWindow).setIcon(iconWithBadge);
      });
    }

  }
}

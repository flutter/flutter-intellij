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
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class ToolWindowBadgeUpdater {
  public static final Color BADGE_PAINT = Color.decode("#5ca963");

  /**
   * Updates the tool window icons for RUN or DEBUG mode with a green badge.
   *
   * @param app     The FlutterApp instance running in a given mode.
   * @param project The current IntelliJ project context.
   */
  public static void updateBadgedIcon(FlutterApp app, Project project) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(Objects.requireNonNull(project));
    final ToolWindow runToolWindow = manager.getToolWindow(ToolWindowId.RUN);
    final ToolWindow debugToolWindow = manager.getToolWindow(ToolWindowId.DEBUG);

    if (Objects.requireNonNull(app).getMode() == RunMode.RUN) {
      if (runToolWindow != null) {
        manager.invokeLater(() -> {
          Icon baseIcon = AllIcons.Toolwindows.ToolWindowRun;
          BadgeIcon iconWithBadge = new BadgeIcon(baseIcon, BADGE_PAINT);
          runToolWindow.setIcon(iconWithBadge);
        });
      }
    }
    else if (app.getMode() == RunMode.DEBUG) {
      manager.invokeLater(() -> {
        // https://github.com/flutter/flutter-intellij/issues/8391
        if (debugToolWindow != null) {
          Icon baseIcon = AllIcons.Toolwindows.ToolWindowDebugger;
          BadgeIcon iconWithBadge = new BadgeIcon(baseIcon, BADGE_PAINT);
          runToolWindow.setIcon(iconWithBadge);
        }
      });
    }
  }

  private static class BadgeIcon implements Icon {
    private final Icon baseIcon;
    private final Color overlayColor;
    private static final float alpha = 1.0F;

    public BadgeIcon(Icon baseIcon, Color overlayColor) {
      this.baseIcon = baseIcon;
      this.overlayColor = overlayColor;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      baseIcon.paintIcon(c, g, x, y);

      Graphics2D g2d = (Graphics2D)g.create();
      try {
        g2d.translate(x, y);

        g2d.setComposite(AlphaComposite.SrcOver.derive(alpha));

        g2d.setColor(overlayColor);
        g2d.fillRect(0, 0, getIconWidth(), getIconHeight());
      }
      finally {
        g2d.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return baseIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return baseIcon.getIconHeight();
    }
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * This class interfaces with the IntelliJ tool window manager and reports tool window
 * usage to analytics.
 */
public class ToolWindowTracker implements ToolWindowManagerListener {

  public static void track(@NotNull Project project, @NotNull Analytics analytics) {
    new ToolWindowTracker(project, analytics);
  }

  private final Analytics myAnalytics;
  private final ToolWindowManagerEx myToolWindowManager;

  private String currentWindowId;

  private ToolWindowTracker(@NotNull Project project, @NotNull Analytics analytics) {
    myAnalytics = analytics;

    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

    project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, this);

    update();
  }

  @Override
  public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
    update();
  }

  private void update() {
    final String newWindow = findWindowId();

    if (!StringUtil.equals(newWindow, currentWindowId)) {
      currentWindowId = newWindow;
      myAnalytics.sendScreenView(currentWindowId);
    }
  }

  @NotNull
  private String findWindowId() {
    final String newWindow = myToolWindowManager.getActiveToolWindowId();
    return newWindow == null ? "editor" : newWindow.toLowerCase();
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;


import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PerformanceOverlayAction extends FlutterViewToggleableAction {

  public static final String SHOW_PERFORMANCE_OVERLAY = "ext.flutter.showPerformanceOverlay";

  PerformanceOverlayAction(@NotNull FlutterApp app) {
    super(app, "Toggle Performance Overlay", "Toggle Performance Overlay", AllIcons.Modules.Library);
    setExtensionCommand(SHOW_PERFORMANCE_OVERLAY);
  }
}
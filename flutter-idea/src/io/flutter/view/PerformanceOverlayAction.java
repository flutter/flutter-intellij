/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.icons.AllIcons;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.vmService.ServiceExtensions;
import org.jetbrains.annotations.NotNull;

public class PerformanceOverlayAction extends FlutterViewToggleableAction<Boolean> {
  public PerformanceOverlayAction(@NotNull FlutterApp app) {
    super(app, AllIcons.Nodes.PpLib, ServiceExtensions.performanceOverlay);
  }
}

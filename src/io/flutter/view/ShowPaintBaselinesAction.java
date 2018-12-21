/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import icons.FlutterIcons;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

class ShowPaintBaselinesAction extends FlutterViewToggleableAction {
  ShowPaintBaselinesAction(@NotNull FlutterApp app, boolean showIcon) {
    super(app, "Show Paint Baselines", null, showIcon ? FlutterIcons.Painting : null);

    setExtensionCommand("ext.flutter.debugPaintBaselinesEnabled");
  }
}

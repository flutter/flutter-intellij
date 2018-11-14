/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.actionSystem.AnActionEvent;
import icons.FlutterIcons;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ShowPaintBaselinesAction extends FlutterViewToggleableAction {
  ShowPaintBaselinesAction(@NotNull FlutterApp app, boolean showIcon) {
    super(app, "Show Paint Baselines", null, showIcon ? FlutterIcons.Painting : null);

    setExtensionCommand("ext.flutter.debugPaintBaselinesEnabled");
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension("ext.flutter.debugPaintBaselinesEnabled", isSelected());
    }
  }
}

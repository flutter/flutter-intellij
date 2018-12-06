/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.icons.AllIcons;
import io.flutter.FlutterBundle;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

class DebugPaintAction extends FlutterViewToggleableAction {
  DebugPaintAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("flutter.view.debugPaint.text"), FlutterBundle.message("flutter.view.debugPaint.description"),
          AllIcons.General.TbShown);

    setExtensionCommand("ext.flutter.debugPaint");
  }
}

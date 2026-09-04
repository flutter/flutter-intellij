/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.wm.ToolWindowId;
import io.flutter.run.FlutterLaunchMode;

public class RunReleaseFlutterApp extends RunFlutterAction {
  private static final String TEXT_DETAIL_MSG_KEY = "app.release.config.action.text";

  public RunReleaseFlutterApp() {
    super(TEXT_DETAIL_MSG_KEY, FlutterLaunchMode.RELEASE, ToolWindowId.RUN);
  }
}

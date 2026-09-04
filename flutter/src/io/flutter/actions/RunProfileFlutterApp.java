/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.wm.ToolWindowId;
import io.flutter.run.FlutterLaunchMode;

public class RunProfileFlutterApp extends RunFlutterAction {
  private static final String TEXT_DETAIL_MSG_KEY = "app.profile.config.action.text";

  public RunProfileFlutterApp() {
    super(TEXT_DETAIL_MSG_KEY, FlutterLaunchMode.PROFILE, ToolWindowId.RUN);
  }
}

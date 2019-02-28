/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.wm.ToolWindowId;
import io.flutter.FlutterBundle;
import io.flutter.run.FlutterLaunchMode;

public class RunReleaseFlutterApp extends RunFlutterAction {
  public static final String TEXT = FlutterBundle.message("app.release.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.release.action.description");
  private static final String TEXT_DETAIL_MSG_KEY = "app.release.config.action.text";

  public RunReleaseFlutterApp() {
    super(TEXT, TEXT_DETAIL_MSG_KEY, DESCRIPTION, AllIcons.Actions.Execute, FlutterLaunchMode.RELEASE, ToolWindowId.RUN
    );
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.wm.ToolWindowId;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;


@SuppressWarnings("ComponentNotRegistered")
public class RunProfileFlutterApp extends FlutterRunModeAction {
  public static final String TEXT = FlutterBundle.message("app.profile.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.profile.action.description");

  public RunProfileFlutterApp() {
    super(TEXT, DESCRIPTION, FlutterIcons.RunProfile, "--profile", ToolWindowId.RUN
    );
  }
}

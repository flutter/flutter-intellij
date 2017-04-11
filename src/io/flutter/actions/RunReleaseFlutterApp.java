/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.wm.ToolWindowId;
import io.flutter.FlutterBundle;


@SuppressWarnings("ComponentNotRegistered")
public class RunReleaseFlutterApp extends RunFlutterAction {
  public static final String TEXT = FlutterBundle.message("app.release.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.release.action.description");

  public RunReleaseFlutterApp() {
    super(TEXT, DESCRIPTION, AllIcons.Actions.Profile, "--release", ToolWindowId.RUN
    );
  }
}

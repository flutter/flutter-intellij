/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;

public class RestartAllFlutterAppsRetarget extends FlutterRetargetAppAction {
  public RestartAllFlutterAppsRetarget() {
    super(RestartAllFlutterApps.ID,
          RestartAllFlutterApps.TEXT,
          RestartAllFlutterApps.DESCRIPTION,
          ActionPlaces.MAIN_TOOLBAR,
          ActionPlaces.NAVIGATION_BAR_TOOLBAR,
          ActionPlaces.MAIN_MENU);
  }
}

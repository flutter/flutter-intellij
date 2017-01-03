/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;

/**
 * A keystroke or tool-bar invoked {@link HotReloadFlutterApp} action.
 */
public class HotReloadFlutterAppRetarget extends FlutterRetargetAction {
  public HotReloadFlutterAppRetarget() {
    super(HotReloadFlutterApp.ID, ActionPlaces.MAIN_TOOLBAR);
  }
}

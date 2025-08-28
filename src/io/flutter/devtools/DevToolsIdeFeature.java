/*
 * Copyright 2023 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

/**
 * This identifies from what feature DevTools is started. See https://github.com/flutter/flutter-intellij/issues/7100 for details.
 */
public enum DevToolsIdeFeature {
  ON_DEBUG_AUTOMATIC("onDebugAutomatic"),
  RUN_CONSOLE("runConsole"),
  TOOL_WINDOW("toolWindow"),
  TOOL_WINDOW_RELOAD("toolWindowReload");

  public final String value;

  DevToolsIdeFeature(String value) {
    this.value = value;
  }
}

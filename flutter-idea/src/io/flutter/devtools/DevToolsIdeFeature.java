/*
 * Copyright 2023 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

public enum DevToolsIdeFeature {
  ON_DEBUG_AUTOMATIC("onDebugAutomatic"),
  RUN_CONSOLE("runConsole"),
  TOOL_WINDOW("toolWindow");

  private final String ideFeature;

  DevToolsIdeFeature(String ideFeature) {
    this.ideFeature = ideFeature;
  }
}

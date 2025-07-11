/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

import org.jetbrains.annotations.NotNull;

/**
 * Persists Flutter settings for a session.
 */
public class FlutterUIConfig {
  private static final @NotNull FlutterUIConfig INSTANCE = new FlutterUIConfig();

  private boolean ignoreOutOfDateFlutterSdks;

  private FlutterUIConfig() {
  }

  public static @NotNull FlutterUIConfig getInstance() {
    return INSTANCE;
  }

  public boolean shouldIgnoreOutOfDateFlutterSdks() {
    return ignoreOutOfDateFlutterSdks;
  }

  public void setIgnoreOutOfDateFlutterSdks() {
    ignoreOutOfDateFlutterSdks = true;
  }
}

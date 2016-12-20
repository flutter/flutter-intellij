/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.settings;

/**
 * Persists Flutter settings for a session.
 */
public class FlutterUIConfig {
  private static final FlutterUIConfig INSTANCE = new FlutterUIConfig();

  private boolean ignoreMismatchedDartSdks;
  private boolean ignoreOutOfDateFlutterSdks;

  private FlutterUIConfig() {}

  public static FlutterUIConfig getInstance() {
    return INSTANCE;
  }

  public boolean shouldIgnoreMismatchedDartSdks() {
    return ignoreMismatchedDartSdks;
  }

  public void setIgnoreMismatchedDartSdks() {
    this.ignoreMismatchedDartSdks = true;
  }

  public boolean shouldIgnoreOutOfDateFlutterSdks() {
    return ignoreOutOfDateFlutterSdks;
  }

  public void setIgnoreOutOfDateFlutterSdks() {
    ignoreOutOfDateFlutterSdks = true;
  }
}

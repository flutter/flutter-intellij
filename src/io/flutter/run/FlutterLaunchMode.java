/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * The Flutter launch mode. This corresponds to the flutter run modes: --debug,
 * --profile, and --release.
 */
public enum FlutterLaunchMode {
  DEBUG("debug"),

  PROFILE("profile"),

  RELEASE("release");

  public static final Key<FlutterLaunchMode> LAUNCH_MODE_KEY = Key.create("FlutterLaunchMode");

  @NotNull
  public static FlutterLaunchMode getMode(@NotNull ExecutionEnvironment env) {
    final FlutterLaunchMode launchMode = env.getUserData(FlutterLaunchMode.LAUNCH_MODE_KEY);
    return launchMode == null ? DEBUG : launchMode;
  }

  final private String myCliCommand;

  FlutterLaunchMode(String cliCommand) {
    this.myCliCommand = cliCommand;
  }

  public String getCliCommand() {
    return myCliCommand;
  }

  /**
   * This mode supports a debug connection (but, doesn't necessarily support breakpoints and debugging).
   */
  public boolean supportsDebugConnection() {
    return this == DEBUG || this == PROFILE;
  }

  public boolean supportsReload() {
    return this == DEBUG;
  }

  public boolean isProfiling() {
    return this == PROFILE;
  }
}

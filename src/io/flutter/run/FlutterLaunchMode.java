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
 * The Flutter launch mode. This coresponds to the flutter run modes: --debug,
 * --profile, and --release.
 */
public enum FlutterLaunchMode {
  DEBUG("debug", true),

  PROFILE("profile", false),

  RELEASE("release", false);

  public static final Key<FlutterLaunchMode> LAUNCH_MODE_KEY = Key.create("FlutterLaunchMode");

  @NotNull
  public static FlutterLaunchMode getMode(@NotNull ExecutionEnvironment env) {
    final FlutterLaunchMode launchMode = env.getUserData(FlutterLaunchMode.LAUNCH_MODE_KEY);
    return launchMode == null ? DEBUG : launchMode;
  }

  final private String myCliCommand;
  final private boolean mySupportsDebugging;

  FlutterLaunchMode(String cliCommand, boolean supportsDebugging) {
    this.myCliCommand = cliCommand;
    this.mySupportsDebugging = supportsDebugging;
  }

  public String getCliCommand() {
    return myCliCommand;
  }

  public boolean supportsDebugging() {
    return mySupportsDebugging;
  }

  public boolean supportsReload() {
    return supportsDebugging();
  }
}

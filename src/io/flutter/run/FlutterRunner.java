/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

/**
 * Runner for non-Bazel run configurations (using the Flutter SDK).
 */
public class FlutterRunner extends LaunchState.Runner<SdkRunConfig> {
  public FlutterRunner() {
    super(SdkRunConfig.class);
  }

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterRunner";
  }

  @Override
  public boolean canRun(SdkRunConfig config) {
    return FlutterSdk.getFlutterSdk(config.getProject()) != null;
  }
}

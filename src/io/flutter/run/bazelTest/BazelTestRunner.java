/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import io.flutter.run.LaunchState;
import org.jetbrains.annotations.NotNull;

public class BazelTestRunner extends LaunchState.Runner<BazelTestConfig> {

  public BazelTestRunner() {
    super(BazelTestConfig.class);
  }

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterBazelTestRunner";
  }
}

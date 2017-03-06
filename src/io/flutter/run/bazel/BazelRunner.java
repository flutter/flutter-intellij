/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import io.flutter.run.Launcher;
import org.jetbrains.annotations.NotNull;

public class BazelRunner extends Launcher.Runner<BazelRunConfig> {

  public BazelRunner() {
    super(BazelRunConfig.class);
  }

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterBazelRunner";
  }
}

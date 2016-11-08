/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunnerParameters;

public class FlutterRunnerParameters extends DartCommandLineRunnerParameters implements Cloneable {
  private boolean myHotMode = true;

  @Override
  protected FlutterRunnerParameters clone() {
    final FlutterRunnerParameters clone = (FlutterRunnerParameters)super.clone();
    return clone;
  }

  public boolean isHotMode() {
    return this.myHotMode;
  }

  public void setHotMode(boolean hotMode) {
    this.myHotMode = hotMode;
  }
}

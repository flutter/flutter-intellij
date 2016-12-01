/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import org.jetbrains.annotations.NonNls;

/**
 * Associate a Java name with the strings passed to Flutter that determine execution mode.
 */
public enum RunMode {

  @NonNls
  DEBUG("debug") {
    public boolean isReloadEnabled() {
      return true;
    }
  },

  @NonNls
  PROFILE("profile"),

  @NonNls
  RUN("run") {
    public boolean isReloadEnabled() {
      return true;
    }
  };

  private final String myModeString;

  RunMode(String modeString) {
    myModeString = modeString;
  }

  public String mode() {
    return myModeString;
  }

  /**
   * Returns true if this is a reload/restart enabled mode (run|debug).
   */
  public boolean isReloadEnabled() {
    return false;
  }
}

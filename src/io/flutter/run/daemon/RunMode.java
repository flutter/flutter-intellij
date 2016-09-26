/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

/**
 * Associate a Java name with the strings passed to Flutter that determine execution mode.
 */
public enum RunMode {

  RELEASE("release"),

  DEBUG("debug") {
    public boolean isDebug() {
      return true;
    }
  },

  PROFILE("profile");

  private String myModeString;

  RunMode(String modeString) {
    myModeString = modeString;
  }

  public String mode() {
    return myModeString;
  }

  public boolean isDebug() {
    return false;
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterSdkVersion {

  @NonNls public static final FlutterSdkVersion UNKNOWN = new FlutterSdkVersion("<unknown>");
  private final String versionString;

  FlutterSdkVersion(String versionString) {
    this.versionString = versionString;
  }

  public static FlutterSdkVersion forVersionString(@Nullable String versionString) {
    return versionString == null ? UNKNOWN : new FlutterSdkVersion(versionString);
  }

  @NotNull
  public String getPresentableName() {
    return versionString;
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterSdkVersion implements Comparable<FlutterSdkVersion> {

  // Minimum SDK known to support hot reload.
  public static final FlutterSdkVersion MIN_SUPPORTED_SDK = FlutterSdkVersion.forVersionString("0.0.3");

  @NonNls private static final FlutterSdkVersion UNKNOWN = new FlutterSdkVersion("0.0.0");

  private final Version myVersion;

  private FlutterSdkVersion(@NotNull String versionString) {
    myVersion = Version.parseVersion(versionString);
  }

  public static FlutterSdkVersion forVersionString(@Nullable String versionString) {
    return versionString == null ? UNKNOWN : new FlutterSdkVersion(versionString);
  }

  public boolean isLessThan(FlutterSdkVersion other) {
    return compareTo(other) < 0;
  }

  @Override
  public String toString() {
    return myVersion.toCompactString();
  }

  @Override
  public int compareTo(@NotNull FlutterSdkVersion other) {
    return myVersion.compareTo(other.myVersion);
  }
}

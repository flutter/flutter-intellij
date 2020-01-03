/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterSdkVersion {
  /**
   * The minimum version we suggest people use.
   */
  private static final FlutterSdkVersion MIN_SUPPORTED_SDK = new FlutterSdkVersion("0.0.12");

  /**
   * The minimum version we suggest people use to enable track-widget-creation.
   * <p>
   * Before this version there were issues if you ran the app from the command
   * line without the flag after running
   */
  private static final FlutterSdkVersion MIN_SAFE_TRACK_WIDGET_CREATION_SDK = new FlutterSdkVersion("0.10.2");

  /**
   * The version of the stable channel that suppoorts --androidx in the create command.
   */
  private static final FlutterSdkVersion MIN_ANDROIDX_SDK = new FlutterSdkVersion("1.7.8");

  @Nullable
  private final Version version;

  @VisibleForTesting
  public FlutterSdkVersion(@NotNull String versionString) {
    version = Version.parseVersion(versionString);
  }

  @NotNull
  public static FlutterSdkVersion readFromSdk(@NotNull VirtualFile sdkHome) {
    final VirtualFile file = sdkHome.findChild("version");

    return readFromFile(file);
  }

  @NotNull
  public static FlutterSdkVersion readFromFile(@Nullable VirtualFile file) {
    if (file == null) {
      return MIN_SUPPORTED_SDK;
    }

    final String versionString = readVersionString(file);

    if (versionString == null) {
      return MIN_SUPPORTED_SDK;
    }

    final FlutterSdkVersion sdkVersion = new FlutterSdkVersion(versionString);
    return sdkVersion.isValid() ? sdkVersion : MIN_SUPPORTED_SDK;
  }

  private static String readVersionString(VirtualFile file) {
    try {
      final String data = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
      for (String line : data.split("\n")) {
        line = line.trim();

        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }

        return line;
      }
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  public boolean isMinRecommendedSupported() {
    assert (MIN_SUPPORTED_SDK.version != null);
    return version != null && version.compareTo(MIN_SUPPORTED_SDK.version) >= 0;
  }

  public boolean isTrackWidgetCreationRecommended() {
    assert (MIN_SAFE_TRACK_WIDGET_CREATION_SDK.version != null);
    return version != null && version.compareTo(MIN_SAFE_TRACK_WIDGET_CREATION_SDK.version) >= 0;
  }

  public boolean isAndroidxSupported() {
    //noinspection ConstantConditions
    return version != null && version.compareTo(MIN_ANDROIDX_SDK.version) >= 0;
  }

  public boolean flutterTestSupportsMachineMode() {
    return isMinRecommendedSupported();
  }

  public boolean flutterTestSupportsFiltering() {
    return isMinRecommendedSupported();
  }

  @Override
  public String toString() {
    return version == null ? "unknown version" : version.toCompactString();
  }

  public boolean isValid() {
    return version != null;
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
   * The version of the stable channel that supports --androidx in the create command.
   */
  private static final FlutterSdkVersion MIN_ANDROIDX_SDK = new FlutterSdkVersion("1.7.8");

  /**
   * The version that supports --dart-define in the run command.
   */
  private static final FlutterSdkVersion MIN_DART_DEFINE_SDK = new FlutterSdkVersion("1.12.0");

  /**
   * The version of the stable channel that supports --androidx in the create command.
   */
  private static final FlutterSdkVersion MIN_PUB_OUTDATED_SDK = new FlutterSdkVersion("1.16.4");

  /**
   * The version that supports --platform in flutter create.
   */
  private static final FlutterSdkVersion MIN_CREATE_PLATFORMS_SDK = new FlutterSdkVersion("1.20.0");

  @Nullable
  private final Version version;
  @Nullable
  private final String versionText;

  @VisibleForTesting
  public FlutterSdkVersion(@Nullable String versionString) {
    version = versionString == null ? null : Version.parseVersion(versionString);
    versionText = versionString;
  }

  @NotNull
  public static FlutterSdkVersion readFromSdk(@NotNull VirtualFile sdkHome) {
    return readFromFile(sdkHome.findChild("version"));
  }

  @NotNull
  private static FlutterSdkVersion readFromFile(@Nullable VirtualFile file) {
    if (file == null) {
      return new FlutterSdkVersion(null);
    }

    final String versionString = readVersionString(file);
    if (versionString == null) {
      return new FlutterSdkVersion(null);
    }

    return new FlutterSdkVersion(versionString);
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

  public boolean isDartDefineSupported() {
    //noinspection ConstantConditions
    return version != null && version.compareTo(MIN_DART_DEFINE_SDK.version) >= 0;
  }

  public boolean flutterCreateSupportsPlatforms() {
    //noinspection ConstantConditions
    return version != null && version.compareTo(MIN_CREATE_PLATFORMS_SDK.version) >= 0;
  }

  public boolean flutterTestSupportsMachineMode() {
    return isMinRecommendedSupported();
  }

  public boolean flutterTestSupportsFiltering() {
    return isMinRecommendedSupported();
  }

  public boolean isPubOutdatedSupported() {
    //noinspection ConstantConditions
    return version != null && version.compareTo(MIN_PUB_OUTDATED_SDK.version) >= 0;
  }

  public boolean isValid() {
    return version != null;
  }

  public String fullVersion() {
    return version == null ? "unknown version" : version.toString();
  }

  /**
   * Return the raw version text from the version file.
   */
  @Nullable
  public String getVersionText() {
    return versionText;
  }

  @Override
  public String toString() {
    return version == null ? "unknown version" : version.toCompactString();
  }
}

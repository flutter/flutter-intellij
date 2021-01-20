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

public class FlutterSdkVersion implements Comparable<FlutterSdkVersion> {
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
  @Nullable
  private final Version betaVersion;
  @Nullable
  private final int masterVersion;

  @VisibleForTesting
  public FlutterSdkVersion(@Nullable String versionString) {
    versionText = versionString;
    if (versionString != null) {
      final String[] split = versionString.split("-");
      version = Version.parseVersion(split[0]);

      if (split.length > 1) {
        betaVersion = Version.parseVersion(split[1]);
        final String[] parts = split[1].split("\\.");
        masterVersion = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
      } else {
        betaVersion = null;
        masterVersion = 0;
      }
    } else {
      version = null;
      betaVersion = null;
      masterVersion = 0;
    }
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

  @Override
  public int compareTo(@NotNull FlutterSdkVersion otherVersion) {
    assert version != null;
    assert otherVersion.version != null;
    final int standardComparisonResult = version.compareTo(otherVersion.version);
    if (standardComparisonResult != 0) {
      return standardComparisonResult;
    }

    // If both versions are on master, we can compare with the beta version and the master version.
    if (masterVersion > 0 && otherVersion.masterVersion > 0) {
      assert betaVersion != null;
      assert otherVersion.betaVersion != null;
      final int betaComparisonResult = betaVersion.compareTo(otherVersion.betaVersion);
      return betaComparisonResult == 0 ? Integer.compare(masterVersion, otherVersion.masterVersion) : betaComparisonResult;
    }

    // TODO(helin24): Implement more thorough/correct comparison for master versions against non-master beta versions.
    // This recognizes all master versions as later than non-master beta versions if the standard version is equal, because a later master
    // version can have smaller beta version numbers than a preceding beta/dev version.
    if (masterVersion > 0 && otherVersion.masterVersion == 0) {
      return 1;
    }

    if (masterVersion == 0 && otherVersion.masterVersion > 0) {
      return -1;
    }

    // Check for beta version strings if standard versions are equivalent.
    if (betaVersion == null && otherVersion.betaVersion == null) {
      return 0;
    }

    if (betaVersion != null && otherVersion.betaVersion != null) {
      return betaVersion.compareTo(otherVersion.betaVersion);
    }

    if (betaVersion == null) {
      return 1;
    }

    return -1;
  }
}

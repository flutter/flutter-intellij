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
   * The version for which the distributed icons can be used.
   */
  @VisibleForTesting
  public static final FlutterSdkVersion DISTRIBUTED_ICONS = new FlutterSdkVersion("3.1.0");
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

  /**
   * The version that supports --config-only when configuring Xcode for opening a project.
   */
  private static final FlutterSdkVersion MIN_XCODE_CONFIG_ONLY = new FlutterSdkVersion("1.22.0-12.0.pre");

  /**
   * The last version of stable that does not support --platform in flutter create.
   */
  private static final FlutterSdkVersion MAX_STABLE_NO_PLATFORMS_SDK = new FlutterSdkVersion("1.22.6");

  /**
   * The version that supports --devtools-server-address in flutter run.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_PASS_DEVTOOLS_SDK = new FlutterSdkVersion("1.26.0-11.0.pre");
  @NotNull
  private static final FlutterSdkVersion MIN_OPTIONAL_PASS_DEVTOOLS_SDK = new FlutterSdkVersion("2.7.0-3.0.pre");

  /**
   * Past this version we want to use the daemon to start DevTools.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_USE_DAEMON_FOR_DEVTOOLS = new FlutterSdkVersion("1.26.0-11.0.pre");

  /**
   * The version that includes the skeleton template.
   */
  private static final FlutterSdkVersion MIN_SKELETON_TEMPLATE = new FlutterSdkVersion("2.5.0");

  /**
   * The version that includes the skeleton template.
   */
  private static final FlutterSdkVersion MIN_PLUGIN_FFI_TEMPLATE = new FlutterSdkVersion("3.0.0");

  /**
   * The version that includes the skeleton template.
   */
  private static final FlutterSdkVersion MIN_EMPTY_PROJECT = new FlutterSdkVersion("3.6.0-0.1.pre");

  /**
   * The version that implements URI mapping for web.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_URI_MAPPING_FOR_WEB = new FlutterSdkVersion("2.13.0-0.1.pre");

  @NotNull
  private static final FlutterSdkVersion MIN_STABLE_WEB_PLATFORM = new FlutterSdkVersion("2.0.0");
  @NotNull
  private static final FlutterSdkVersion MIN_STABLE_WINDOWS_PLATFORM = new FlutterSdkVersion("2.10.0");
  @NotNull
  private static final FlutterSdkVersion MIN_STABLE_LINUX_PLATFORM = new FlutterSdkVersion("3.0.0");
  @NotNull
  private static final FlutterSdkVersion MIN_STABLE_MACOS_PLATFORM = new FlutterSdkVersion("3.0.0");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_DEVTOOLS_PATH_URLS = new FlutterSdkVersion("3.3.0");

  @Nullable
  private final Version version;
  @Nullable
  private final String versionText;
  @Nullable
  private final Version betaVersion;
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
      }
      else {
        betaVersion = null;
        masterVersion = 0;
      }
    }
    else {
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

  public boolean isXcodeConfigOnlySupported() {
    //noinspection ConstantConditions
    return version != null && version.compareTo(MIN_XCODE_CONFIG_ONLY.version) >= 0;
  }

  public boolean flutterCreateSupportsPlatforms() {
    //noinspection ConstantConditions
    return version != null && version.compareTo(MIN_CREATE_PLATFORMS_SDK.version) >= 0;
  }

  public boolean stableChannelSupportsPlatforms() {
    //noinspection ConstantConditions
    return version != null && version.compareTo(MAX_STABLE_NO_PLATFORMS_SDK.version) > 0;
  }

  public boolean flutterRunSupportsDevToolsUrl() {
    return version != null && this.compareTo(MIN_PASS_DEVTOOLS_SDK) >= 0 && this.compareTo(MIN_OPTIONAL_PASS_DEVTOOLS_SDK) < 0;
  }

  public boolean useDaemonForDevTools() {
    return version != null && this.compareTo(MIN_PASS_DEVTOOLS_SDK) >= 0;
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

  public boolean isSkeletonTemplateAvailable() {
    return version != null && version.compareTo(MIN_SKELETON_TEMPLATE.version) >= 0;
  }

  public boolean isPluginFfiTemplateAvailable() {
    return version != null && version.compareTo(MIN_PLUGIN_FFI_TEMPLATE.version) >= 0;
  }

  public boolean isEmptyProjectAvailable() {
    return version != null && version.compareTo(MIN_EMPTY_PROJECT.version) >= 0;
  }

  public boolean isUriMappingSupportedForWeb() {
    return version != null && this.compareTo(MIN_URI_MAPPING_FOR_WEB) >= 0;
  }

  public boolean isWebPlatformStable() {
    return version != null && this.compareTo(MIN_STABLE_WEB_PLATFORM) >= 0;
  }

  public boolean isWindowsPlatformStable() {
    return version != null && this.compareTo(MIN_STABLE_WINDOWS_PLATFORM) >= 0;
  }

  public boolean isLinuxPlatformStable() {
    return version != null && this.compareTo(MIN_STABLE_LINUX_PLATFORM) >= 0;
  }

  public boolean isMacOSPlatformStable() {
    return version != null && this.compareTo(MIN_STABLE_MACOS_PLATFORM) >= 0;
  }

  public boolean canUseDistributedIcons() {
    return version != null && this.compareTo(DISTRIBUTED_ICONS) >= 0;
  }

  public boolean canUseDevToolsPathUrls() {
    return version != null && this.compareTo(MIN_SUPPORTS_DEVTOOLS_PATH_URLS) >= 0;
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

    // Check for beta version strings if standard versions are equivalent.
    if (betaVersion == null && otherVersion.betaVersion == null) {
      return 0;
    }
    else if (betaVersion == null) {
      return 1;
    }
    else if (otherVersion.betaVersion == null) {
      return -1;
    }

    final int betaComparisonResult = betaVersion.compareTo(otherVersion.betaVersion);

    if (betaComparisonResult != 0) {
      return betaComparisonResult;
    }

    // Check master version ints if both have master version ints and versions are otherwise equivalent.
    // Otherwise, the version without a master version is further ahead.
    if (masterVersion != 0 && otherVersion.masterVersion != 0) {
      return Integer.compare(masterVersion, otherVersion.masterVersion);
    }
    else if (masterVersion != 0) {
      return -1;
    }
    else if (otherVersion.masterVersion != 0) {
      return 1;
    }
    else {
      return 0;
    }
  }
}

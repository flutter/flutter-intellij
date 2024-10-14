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
import java.util.Objects;

public final class FlutterSdkVersion implements Comparable<FlutterSdkVersion> {
  /**
   * The version for which the distributed icons can be used.
   */
  @VisibleForTesting
  @NotNull
  public static final FlutterSdkVersion DISTRIBUTED_ICONS = new FlutterSdkVersion("3.1.0");

  /**
   * The minimum version we suggest people use.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTED_SDK = new FlutterSdkVersion("0.0.12");

  /**
   * The minimum version we suggest people use to enable track-widget-creation.
   * <p>
   * Before this version there were issues if you ran the app from the command
   * line without the flag after running
   */
  @NotNull
  private static final FlutterSdkVersion MIN_SAFE_TRACK_WIDGET_CREATION_SDK = new FlutterSdkVersion("0.10.2");

  /**
   * The version that supports --dart-define in the run command.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_DART_DEFINE_SDK = new FlutterSdkVersion("1.12.0");

  /**
   * The version of the stable channel that supports --androidx in the create command.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_PUB_OUTDATED_SDK = new FlutterSdkVersion("1.16.4");

  /**
   * The version that supports --platform in flutter create.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_CREATE_PLATFORMS_SDK = new FlutterSdkVersion("1.20.0");

  /**
   * The version that supports --config-only when configuring Xcode for opening a project.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_XCODE_CONFIG_ONLY = new FlutterSdkVersion("1.22.0-12.0.pre");

  /**
   * The last version of stable that does not support --platform in flutter create.
   */
  @NotNull
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
  @NotNull
  private static final FlutterSdkVersion MIN_SKELETON_TEMPLATE = new FlutterSdkVersion("2.5.0");

  /**
   * The version that includes the skeleton template.
   */
  @NotNull
  private static final FlutterSdkVersion MIN_PLUGIN_FFI_TEMPLATE = new FlutterSdkVersion("3.0.0");

  /**
   * The version that includes the skeleton template.
   */
  @NotNull
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

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_TOOL_EVENT_STREAM = new FlutterSdkVersion("3.7.1");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_DEEP_LINKS_TOOL = new FlutterSdkVersion("3.19.0");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_DEVTOOLS_MULTI_EMBED = new FlutterSdkVersion("3.23.0-0.1.pre");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_DTD = new FlutterSdkVersion("3.22.0");

  @NotNull
  // TODO(helin24): Update with the right version.
  private static final FlutterSdkVersion MIN_SDK_SUPPORTED = new FlutterSdkVersion("0.0.1");

  @Nullable
  private final Version version;
  @Nullable
  private final String versionText;
  @Nullable
  private final Version betaVersion;
  private final int mainVersion;

  @VisibleForTesting
  public FlutterSdkVersion(@Nullable String versionString) {
    versionText = versionString;
    if (versionString != null) {
      final @NotNull String[] split = versionString.split("-");
      version = Version.parseVersion(split[0]);

      if (split.length > 1) {
        betaVersion = Version.parseVersion(split[1]);
        final @NotNull String[] parts = split[1].split("\\.");
        mainVersion = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
      }
      else {
        betaVersion = null;
        mainVersion = 0;
      }
    }
    else {
      version = null;
      betaVersion = null;
      mainVersion = 0;
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

  @Nullable
  private static String readVersionString(@NotNull VirtualFile file) {
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

  private boolean supportsVersion(@NotNull FlutterSdkVersion otherVersion) {
    return this.compareTo(otherVersion) >= 0;
  }

  public boolean isMinRecommendedSupported() {
    return supportsVersion(MIN_SUPPORTED_SDK);
  }

  public boolean isTrackWidgetCreationRecommended() {
    return supportsVersion(MIN_SAFE_TRACK_WIDGET_CREATION_SDK);
  }

  public boolean isDartDefineSupported() {
    return supportsVersion(MIN_DART_DEFINE_SDK);
  }

  public boolean isXcodeConfigOnlySupported() {
    return supportsVersion(MIN_XCODE_CONFIG_ONLY);
  }

  public boolean flutterCreateSupportsPlatforms() {
    return supportsVersion(MIN_CREATE_PLATFORMS_SDK);
  }

  public boolean stableChannelSupportsPlatforms() {
    return supportsVersion(MAX_STABLE_NO_PLATFORMS_SDK);
  }

  public boolean flutterRunSupportsDevToolsUrl() {
    return this.compareTo(MIN_PASS_DEVTOOLS_SDK) >= 0 && this.compareTo(MIN_OPTIONAL_PASS_DEVTOOLS_SDK) < 0;
  }

  public boolean useDaemonForDevTools() {
    return supportsVersion(MIN_PASS_DEVTOOLS_SDK);
  }

  public boolean flutterTestSupportsMachineMode() {
    return isMinRecommendedSupported();
  }

  public boolean flutterTestSupportsFiltering() {
    return isMinRecommendedSupported();
  }

  public boolean isPubOutdatedSupported() {
    return supportsVersion(MIN_PUB_OUTDATED_SDK);
  }

  public boolean isSkeletonTemplateAvailable() {
    return supportsVersion(MIN_SKELETON_TEMPLATE);
  }

  public boolean isPluginFfiTemplateAvailable() {
    return supportsVersion(MIN_PLUGIN_FFI_TEMPLATE);
  }

  public boolean isEmptyProjectAvailable() {
    return supportsVersion(MIN_EMPTY_PROJECT);
  }

  public boolean isUriMappingSupportedForWeb() {
    return supportsVersion(MIN_URI_MAPPING_FOR_WEB);
  }

  public boolean isWebPlatformStable() {
    return supportsVersion(MIN_STABLE_WEB_PLATFORM);
  }

  public boolean isWindowsPlatformStable() {
    return supportsVersion(MIN_STABLE_WINDOWS_PLATFORM);
  }

  public boolean isLinuxPlatformStable() {
    return supportsVersion(MIN_STABLE_LINUX_PLATFORM);
  }

  public boolean isMacOSPlatformStable() {
    return supportsVersion(MIN_STABLE_MACOS_PLATFORM);
  }

  public boolean canUseDistributedIcons() {
    return supportsVersion(DISTRIBUTED_ICONS);
  }

  public boolean canUseDevToolsPathUrls() {
    return supportsVersion(MIN_SUPPORTS_DEVTOOLS_PATH_URLS);
  }

  public boolean canUseToolEventStream() {
    return supportsVersion(MIN_SUPPORTS_TOOL_EVENT_STREAM);
  }

  public boolean canUseDeepLinksTool() {
    return supportsVersion(MIN_SUPPORTS_DEEP_LINKS_TOOL);
  }

  public boolean canUseDevToolsMultiEmbed() {
    return supportsVersion(MIN_SUPPORTS_DEVTOOLS_MULTI_EMBED);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean canUseDtd() {
    return supportsVersion(MIN_SUPPORTS_DTD);
  }

  public boolean sdkIsSupported() {
    return supportsVersion(MIN_SDK_SUPPORTED);
  }

  public boolean isValid() {
    return version != null;
  }

  @NotNull
  public String fullVersion() {
    return version == null ? "unknown version" : Objects.requireNonNull(version.toString());
  }

  /**
   * Return the raw version text from the version file.
   */
  @Nullable
  public String getVersionText() {
    return versionText;
  }

  @Override
  @NotNull
  public String toString() {
    return version == null ? "unknown version" : Objects.requireNonNull(version.toCompactString());
  }

  @Override
  public int compareTo(@NotNull FlutterSdkVersion otherVersion) {
    if (version == null) return -1;
    if (otherVersion.version == null) return 1;
    final int standardComparisonResult = version.compareTo(otherVersion.version);
    if (standardComparisonResult != 0) {
      return standardComparisonResult;
    }

    // If standard versions are equivalent, check for beta version strings.
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

    // If both have main version ints and the versions are otherwise equivalent,
    // compare the main version ints.
    // Otherwise, the version without a main version is further ahead.
    if (mainVersion != 0 && otherVersion.mainVersion != 0) {
      return Integer.compare(mainVersion, otherVersion.mainVersion);
    }
    else if (mainVersion != 0) {
      return -1;
    }
    else if (otherVersion.mainVersion != 0) {
      return 1;
    }
    else {
      return 0;
    }
  }
}

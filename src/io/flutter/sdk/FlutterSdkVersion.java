/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public final class FlutterSdkVersion implements Comparable<FlutterSdkVersion> {
  /**
   * The minimum version of the Flutter SDK that is still supported. A version less than this should trigger a warning to users that
   * the Flutter plugin no longer supports the SDK version and we will not be fixing issues resulting from using this version.
   * <p>
   * Note, this is for the Flutter SDK version, not the Dart SDK version, this mapping can be found:
   * <a href="https://docs.flutter.dev/release/archive">Flutter SDK Release Archive list</a>.
   * <p>
   * This version was updated last on December 5, 2025.
   */
  @VisibleForTesting
  @NotNull
  public static final FlutterSdkVersion MIN_SDK_SUPPORTED = new FlutterSdkVersion("3.16");

  /**
   * The minimum version of the Flutter SDK that will be supported for 3 more months. A version less than this is either not supported or
   * should trigger a warning to users that support for the version will be gone in an upcoming plugin version.
   * <p>
   * Note, this is for the Flutter SDK version, not the Dart SDK version, this mapping can be found:
   * <a href="https://docs.flutter.dev/release/archive">Flutter SDK Release Archive list</a>.
   * <p>
   * This version was updated last on December 5, 2025.
   */
  @VisibleForTesting
  @NotNull
  public static final FlutterSdkVersion MIN_SDK_WITHOUT_SUNSET_WARNING = new FlutterSdkVersion("3.19.4");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_TOOL_EVENT_STREAM = new FlutterSdkVersion("3.7.1");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_DEEP_LINKS_TOOL = new FlutterSdkVersion("3.19.0");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_DEVTOOLS_MULTI_EMBED = new FlutterSdkVersion("3.23.0-0.1.pre");

  @NotNull
  private static final FlutterSdkVersion MIN_SUPPORTS_DTD = new FlutterSdkVersion("3.22.0");

  @NotNull
  public static final FlutterSdkVersion MIN_SUPPORTS_PROPERTY_EDITOR = new FlutterSdkVersion("3.32.0-0.1.pre");

  @NotNull
  public static final FlutterSdkVersion MIN_SUPPORTS_WIDGET_PREVIEW = new FlutterSdkVersion("3.38.0-0.0.pre");

  @NotNull
  public static final String UNKNOWN_VERSION = "unknown version";

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
    // First check for the file at <sdkHome>/bin/cache/flutter.version.json
    // https://github.com/flutter/flutter-intellij/issues/8465
    final VirtualFile versionFile = sdkHome.findFileByRelativePath("bin/cache/flutter.version.json");
    if (versionFile != null && versionFile.exists() && !versionFile.isDirectory()) {
      return readFromFile(versionFile);
    }

    // Fallback to the old location at <sdkHome>/version
    return readFromFile(sdkHome.findChild("version"));
  }

  @NotNull
  private static FlutterSdkVersion readFromFile(@Nullable VirtualFile file) {
    if (file == null) {
      return new FlutterSdkVersion(null);
    }
    return new FlutterSdkVersion(readVersionString(file));
  }

  @Nullable
  private static String readVersionString(@NotNull VirtualFile file) {
    try {
      final String data = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);

      // Check if the content is a JSON object.
      // This tests to see if it is of the format provided by <sdkHome>/bin/cache/flutter.version.json files
      // https://github.com/flutter/flutter-intellij/issues/8465
      if (data.trim().startsWith("{")) {
        try {
          final Map<String, Object> json = new Gson().fromJson(data, new TypeToken<Map<String, Object>>() {
          }.getType());
          if (json != null && json.containsKey("frameworkVersion")) {
            return (String)json.get("frameworkVersion");
          }
        }
        catch (com.google.gson.JsonSyntaxException e) {
          return null;
        }
      }
      else {
        // Otherwise, handle the old plain text format.
        for (String line : data.split("\n")) {
          line = line.trim();

          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          return line;
        }
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

  public boolean canUseToolEventStream() {
    return supportsVersion(MIN_SUPPORTS_TOOL_EVENT_STREAM);
  }

  public boolean canUseDeepLinksTool() {
    return supportsVersion(MIN_SUPPORTS_DEEP_LINKS_TOOL);
  }

  public boolean canUsePropertyEditor() {
    return supportsVersion(MIN_SUPPORTS_PROPERTY_EDITOR);
  }

  public boolean canUseWidgetPreview() {
    return supportsVersion(MIN_SUPPORTS_WIDGET_PREVIEW);
  }

  public boolean canUseDevToolsMultiEmbed() {
    return supportsVersion(MIN_SUPPORTS_DEVTOOLS_MULTI_EMBED);
  }

  /**
   * @noinspection BooleanMethodIsAlwaysInverted
   */
  public boolean canUseDtd() {
    return supportsVersion(MIN_SUPPORTS_DTD);
  }

  public boolean isSDKSupported() {
    return supportsVersion(MIN_SDK_SUPPORTED);
  }

  public boolean isSDKAboutToSunset() {
    return this.compareTo(MIN_SDK_SUPPORTED) >= 0 && this.compareTo(MIN_SDK_WITHOUT_SUNSET_WARNING) < 0;
  }

  public boolean isValid() {
    return version != null;
  }

  @NotNull
  public String fullVersion() {
    return version == null ? UNKNOWN_VERSION : Objects.requireNonNull(version.toString());
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
    return version == null ? UNKNOWN_VERSION : Objects.requireNonNull(version.toCompactString());
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

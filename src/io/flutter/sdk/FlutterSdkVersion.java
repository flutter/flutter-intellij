/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import com.intellij.openapi.util.Version;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FlutterSdkVersion {
  /**
   * The minimum version we suggest people use.
   */
  private static final FlutterSdkVersion MIN_SUPPORTED_SDK = new FlutterSdkVersion("0.0.12");

  private final Version version;

  private FlutterSdkVersion(@NotNull String versionString) {
    version = Version.parseVersion(versionString);
  }

  @NotNull
  public static FlutterSdkVersion readFromSdk(@NotNull VirtualFile sdkHome) {
    final VirtualFile file = sdkHome.findChild("version");

    if (file == null) {
      return MIN_SUPPORTED_SDK;
    }

    final String versionString = readVersionString(file);

    if (versionString == null) {
      return MIN_SUPPORTED_SDK;
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
    return version.compareTo(MIN_SUPPORTED_SDK.version) >= 0;
  }

  public boolean flutterTestSupportsMachineMode() {
    return isMinRecommendedSupported();
  }

  public boolean flutterTestSupportsFiltering() {
    return isMinRecommendedSupported();
  }

  @Override
  public String toString() {
    return version.toCompactString();
  }
}

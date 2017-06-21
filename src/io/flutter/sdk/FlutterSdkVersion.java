/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FlutterSdkVersion {

  /**
   * The minimum version known to support hot reload.
   */
  private static final FlutterSdkVersion MIN_SUPPORTED_SDK = new FlutterSdkVersion("0.0.3");

  /**
   * The minimum version where the 'flutter test' supports --machine.
   */
  private static final FlutterSdkVersion MIN_TEST_MACHINE_MODE = new FlutterSdkVersion("0.0.11");

  /**
   * Cache from version file and its modification date to its contents.
   */
  private static final Map<Pair<File, Long>, FlutterSdkVersion> cache = new HashMap<>();

  private final Version version;

  private FlutterSdkVersion(@NotNull String versionString) {
    version = Version.parseVersion(versionString);
  }

  @NotNull
  public static FlutterSdkVersion readFromSdk(@NotNull VirtualFile sdkHome) {
    final File versionFile = new File(sdkHome.getPath() + "/VERSION");

    // Use the cache if the file's last modfication date didn't change.
    // But we still stat the file every time this is called. Not sure the cache saves us much?
    // TODO(skybrian) should we use the VirtualFile cache instead?
    final Pair<File, Long> key = Pair.create(versionFile, versionFile.lastModified());
    if (cache.containsKey(key)) {
      return cache.get(key);
    }

    final String versionString = readVersionFromFile(versionFile);
    if (versionString == null) {
      LOG.warn("Unable to find Flutter SDK version at " + sdkHome.getPath());
    }

    // If we don't have a version file at all, assume it's a supported version and don't complain.
    final FlutterSdkVersion version = versionString == null ? MIN_SUPPORTED_SDK : new FlutterSdkVersion(versionString);

    cache.put(Pair.create(versionFile, versionFile.lastModified()), version);
    return version;
  }

  public boolean isSupported() {
    return version.compareTo(MIN_SUPPORTED_SDK.version) >= 0;
  }

  public boolean flutterTestSupportsMachineMode() {
    return version.compareTo(MIN_TEST_MACHINE_MODE.version) >= 0;
  }

  @Override
  public String toString() {
    return version.toCompactString();
  }

  private static String readVersionFromFile(File versionFile) {
    if (!versionFile.isFile() || versionFile.length() >= 1000) {
      return null;
    }

    try {
      final String content = FileUtil.loadFile(versionFile).trim();
      final int index = content.lastIndexOf('\n');
      if (index < 0) return content;
      return content.substring(index + 1).trim();
    }
    catch (IOException e) {
      /* ignore */
      return null;
    }
  }

  private static final Logger LOG = Logger.getInstance(FlutterSdkVersion.class);
}

/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DartPluginVersion implements Comparable<DartPluginVersion> {

  @Nullable
  private final String rawVersionString;

  @Nullable
  private final Version version;

  public DartPluginVersion(@Nullable String versionString) {
    rawVersionString = versionString;
    version = Version.parseVersion(versionString);

    System.out.println("major:");
    System.out.println(version.major);
    System.out.println("minor:");
    System.out.println(version.minor);
    System.out.println("bug fix:");
    System.out.println(version.bugfix);
  }


  @Override
  public int compareTo(@NotNull DartPluginVersion otherVersion) {
    if (rawVersionString == null) return -1;
    if (otherVersion.rawVersionString == null) return 1;
    return version.compareTo(otherVersion.version);
  }

  public boolean supportsPropertyEditor() {
    if (version == null) return false;
    final int major = version.major;

    if (major == 243) {
      return this.compareTo(new DartPluginVersion("243.26753.1")) >= 0;
    }

    if (major == 251) {
      return this.compareTo(new DartPluginVersion("251.23774.318")) >= 0;
    }

    return major >= 244;
  }
}

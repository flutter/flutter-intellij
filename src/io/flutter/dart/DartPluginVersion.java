/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents and manages Dart plugin version information.
 * This class provides functionality to parse, compare, and check compatibility
 * of different Dart plugin versions. It supports version-specific feature
 * detection, such as determining if a particular version supports the property editor.
 * <p>
 * Versions can be compared using standard comparison operators through the
 * {@link Comparable} interface implementation.
 */
public class DartPluginVersion implements Comparable<DartPluginVersion> {

  @Nullable
  private final String rawVersionString;

  @Nullable
  private final Version version;

  public DartPluginVersion(@Nullable String versionString) {
    rawVersionString = versionString;
    version = versionString == null ? null : Version.parseVersion(versionString);
  }

  @Override
  public int compareTo(@NotNull DartPluginVersion otherVersion) {
    if (rawVersionString == null || version == null) return -1;
    if (otherVersion.rawVersionString == null || otherVersion.version == null) return 1;
    return version.compareTo(otherVersion.version);
  }

  public boolean supportsPropertyEditor() {
    if (version == null) {
      return false;
    }
    final int major = version.major;
    if (major == 243) {
      return this.compareTo(new DartPluginVersion("243.26753.1")) >= 0;
    }
    else if (major == 251) {
      return this.compareTo(new DartPluginVersion("251.23774.318")) >= 0;
    }
    else {
      return major >= 244;
    }
  }
}

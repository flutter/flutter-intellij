/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.util.Pair;
import io.flutter.bazel.PluginConfig;
import io.flutter.bazel.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeWorkspaceFactory {
  /**
   * Creates a {@code Workspace} for testing and a {@code MockVirtualFileSystem} with the expected Flutter script files.
   */
  @NotNull
  public static Pair.NonNull<MockVirtualFileSystem, Workspace> createWorkspaceAndFilesystem(
    @Nullable String daemonScript,
    @Nullable String doctorScript,
    @Nullable String launchScript,
    @Nullable String testScript,
    @Nullable String sdkHome,
    @Nullable String versionFile
  ) {
    MockVirtualFileSystem fs = new MockVirtualFileSystem();
    fs.file("/workspace/WORKSPACE", "");
    if (daemonScript != null) {
      fs.file("/workspace/" + daemonScript, "");
    }
    if (doctorScript != null) {
      fs.file("/workspace/" + doctorScript, "");
    }
    if (launchScript != null) {
      fs.file("/workspace/" + launchScript, "");
    }
    if (testScript != null) {
      fs.file("/workspace/" + testScript, "");
    }
    if (sdkHome != null) {
      fs.file("/workspace/" + sdkHome, "");
    }
    if (versionFile != null) {
      fs.file("/workspace/" + versionFile, "");
    }
    return Pair.createNonNull(
      fs,
      Workspace.forTest(
        fs.findFileByPath("/workspace/"),
        PluginConfig.forTest(
          daemonScript,
          doctorScript,
          launchScript,
          testScript,
          sdkHome,
          versionFile
        )
      )
    );
  }

  /**
   * Creates a {@code Workspace} for testing and a {@code MockVirtualFileSystem} with the expected Flutter script files.
   * <p>
   * Uses default values for all fields.
   */
  @NotNull
  public static Pair.NonNull<MockVirtualFileSystem, Workspace> createWorkspaceAndFilesystem() {
    return createWorkspaceAndFilesystem(
      "scripts/flutter-daemon.sh",
      "scripts/flutter-doctor.sh",
      "scripts/bazel-run.sh",
      "scripts/flutter-test.sh",
      "scripts/",
      "flutter-version"
    );
  }
}

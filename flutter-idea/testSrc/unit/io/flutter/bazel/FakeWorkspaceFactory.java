/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.util.Pair;
import org.dartlang.vm.service.element.Null;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeWorkspaceFactory {
  /**
   * Creates a {@code Workspace} for testing and a {@code MockVirtualFileSystem} with the expected Flutter script files.
   */
  @NotNull
  public static Pair.NonNull<MockVirtualFileSystem, Workspace> createWorkspaceAndFilesystem(
    @Nullable String daemonScript,
    @Nullable String devToolsScript,
    @Nullable String doctorScript,
    @Nullable String testScript,
    @Nullable String runScript,
    @Nullable String syncScript,
    @Nullable String sdkHome,
    @Nullable String versionFile,
    @Nullable String requiredIJPluginID,
    @Nullable String requiredIJPluginMessage,
    @Nullable String configWarningMessage,
    @Nullable String updatedIosRunMessage
  ) {
    final MockVirtualFileSystem fs = new MockVirtualFileSystem();
    fs.file("/workspace/WORKSPACE", "");
    if (daemonScript != null) {
      fs.file("/workspace/" + daemonScript, "");
    }
    if (devToolsScript != null) {
      fs.file("/workspace/" + devToolsScript, "");
    }
    if (doctorScript != null) {
      fs.file("/workspace/" + doctorScript, "");
    }
    if (testScript != null) {
      fs.file("/workspace/" + testScript, "");
    }
    if (runScript != null) {
      fs.file("/workspace/" + runScript, "");
    }
    if (syncScript != null) {
      fs.file("/workspace/" + syncScript, "");
    }
    if (sdkHome != null) {
      fs.file("/workspace/" + sdkHome, "");
    }
    if (requiredIJPluginID != null) {
      fs.file("/workspace/" + requiredIJPluginID, "");
    }
    if (requiredIJPluginMessage != null) {
      fs.file("/workspace/" + requiredIJPluginMessage, "");
    }
    if (configWarningMessage != null) {
      fs.file("/workspace/" + configWarningMessage, "");
    }
    return Pair.createNonNull(
      fs,
      Workspace.forTest(
        fs.findFileByPath("/workspace/"),
        PluginConfig.forTest(
          daemonScript,
          devToolsScript,
          doctorScript,
          testScript,
          runScript,
          syncScript,
          sdkHome,
          requiredIJPluginID,
          requiredIJPluginMessage,
          configWarningMessage,
          updatedIosRunMessage
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
      "scripts/flutter-devtools.sh",
      "scripts/flutter-doctor.sh",
      "scripts/flutter-test.sh",
      "scripts/flutter-run.sh",
      "scripts/flutter-sync.sh",
      "scripts/",
      "flutter-version",
      "some.ij.plugin.id",
      "Some IJ Plugin ID Message",
      "Config warning message",
      "Updated iOS run message"
    );
  }
}

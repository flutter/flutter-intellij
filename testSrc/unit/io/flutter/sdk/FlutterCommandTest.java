/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import io.flutter.ide.FlutterCodeInsightFixtureTestCase;

import java.io.File;
import java.util.List;

public class FlutterCommandTest extends FlutterCodeInsightFixtureTestCase {

  /**
   * Creates a real {@link FlutterSdk} backed by a minimal fake SDK directory in a temp folder.
   * <p>
   * The directory contains only the files and folders that {@link FlutterSdkUtil#isFlutterSdkHome}
   * checks for, so {@link FlutterSdk#forPath} accepts it. This avoids reflection and mocking.
   * The directory is deleted when the test finishes.
   */
  private FlutterSdk createFlutterSdk() throws Exception {
    final File sdkDir = FileUtil.createTempDirectory("fake_flutter_sdk", null);
    Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(sdkDir));

    assertTrue(new File(sdkDir, "packages/flutter").mkdirs());
    assertTrue(new File(sdkDir, "packages/flutter/pubspec.yaml").createNewFile());
    assertTrue(new File(sdkDir, "bin").mkdirs());
    assertTrue(new File(sdkDir, "bin/flutter").createNewFile());
    assertTrue(new File(sdkDir, "bin/cache/dart-sdk/lib").mkdirs());

    // forPath() looks the directory up in the VFS, so make sure the VFS knows about the new files.
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sdkDir);

    final FlutterSdk sdk = FlutterSdk.forPath(sdkDir.getPath());
    assertNotNull("Fake Flutter SDK was not recognized", sdk);
    return sdk;
  }

  private List<String> parametersFor(FlutterCommand.Type type) throws Exception {
    final FlutterCommand command = new FlutterCommand(createFlutterSdk(), null, type);
    final GeneralCommandLine line = command.createGeneralCommandLine(getProject());
    return line.getParametersList().getList();
  }

  public void testRunCommandDoesNotAddNoColor() throws Exception {
    final List<String> params = parametersFor(FlutterCommand.Type.RUN);

    assertEquals(List.of("run"), params);
    assertFalse(params.contains("--no-color"));
  }

  public void testPubGetCommandDoesNotAddNoColor() throws Exception {
    final List<String> params = parametersFor(FlutterCommand.Type.PUB_GET);

    assertEquals(List.of("pub", "get"), params);
    assertFalse(params.contains("--no-color"));
  }

  public void testDoctorCommandDoesNotAddNoColor() throws Exception {
    final List<String> params = parametersFor(FlutterCommand.Type.DOCTOR);

    assertEquals(List.of("doctor", "--verbose"), params);
    assertFalse(params.contains("--no-color"));
  }
}
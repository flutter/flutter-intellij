/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.dart.DartPlugin;
import io.flutter.testing.FlutterModuleFixture;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FlutterSdkUtilTest {
  @Rule
  public ProjectFixture projectFixture = Testing.makeCodeInsightModule();

  @Rule
  public FlutterModuleFixture flutterFixture = new FlutterModuleFixture(projectFixture);

  @Test
  public void shouldInstallFlutterSDK() throws ExecutionException {
    // All of FlutterTestUtils is exercised before getting here.
    assertTrue("Test jig setup failed", true);

    // Verify Flutter SDK is installed correctly.
    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(projectFixture.getProject());
    assertNotNull(flutterSdk);
    final String path = System.getProperty("flutter.sdk");
    assertEquals("Incorrect Flutter SDK path", flutterSdk.getHomePath(), path);

    // Verify Dart SDK is the one distributed with Flutter.
    final DartSdk dartSdk = DartPlugin.getDartSdk(projectFixture.getProject());
    assertNotNull(dartSdk);
    assertTrue("Dart SDK not found in Flutter SDK installation", dartSdk.getHomePath().startsWith(flutterSdk.getHomePath()));

    // Check SDK utilities.
    final String toolPath = FlutterSdkUtil.pathToFlutterTool(flutterSdk.getHomePath());
    assertEquals("Incorrect path to flutter command", toolPath, path + "/bin/flutter");
    assertTrue(FlutterSdkUtil.isFlutterSdkHome(path));
  }
}

/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XcodeUtilsTest {
  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  private File developerDir;

  @Before
  public void setUp() {
    developerDir = new File(tempFolder.getRoot(), "Xcode.app/Contents/Developer");
    assertTrue(developerDir.mkdirs());
  }

  @Test
  public void usesDeviceHubWhenPresent() {
    final File deviceHub = createApp(new File(developerDir.getParentFile(), "Applications/DeviceHub.app"));

    assertEquals(deviceHub.getPath(), XcodeUtils.getSimulatorAppPath(developerDir.getPath()));
  }

  @Test
  public void prefersDeviceHubOverSimulator() {
    final File deviceHub = createApp(new File(developerDir.getParentFile(), "Applications/DeviceHub.app"));
    createApp(new File(developerDir, "Applications/Simulator.app"));

    assertEquals(deviceHub.getPath(), XcodeUtils.getSimulatorAppPath(developerDir.getPath()));
  }

  @Test
  public void fallsBackToSimulatorWhenDeviceHubMissing() {
    final File simulator = createApp(new File(developerDir, "Applications/Simulator.app"));

    assertEquals(simulator.getPath(), XcodeUtils.getSimulatorAppPath(developerDir.getPath()));
  }

  @Test
  public void fallsBackToAppNameWhenNoAppFound() {
    assertEquals("Simulator.app", XcodeUtils.getSimulatorAppPath(developerDir.getPath()));
  }

  private static File createApp(File app) {
    assertTrue(app.mkdirs());
    return app;
  }
}

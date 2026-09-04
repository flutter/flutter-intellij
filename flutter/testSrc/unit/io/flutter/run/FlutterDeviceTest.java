/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlutterDeviceTest {

  @Test
  public void runConfigurationNameIncludesNormalizedDeviceName() {
    final FlutterDevice device = new FlutterDevice("ios-device", "iPhone_16_Pro", "ios", false);

    assertEquals("main.dart (iPhone 16 Pro)", device.withRunConfigurationName("main.dart"));
  }
}

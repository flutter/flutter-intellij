/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlutterSdkVersionTest {
  @Test
  public void parsesGoodVersion() {
    final FlutterSdkVersion version = new FlutterSdkVersion("0.0.12");
    assertTrue(version.isValid());
  }

  @Test
  public void handlesBadVersion() {
    final FlutterSdkVersion version = new FlutterSdkVersion("unknown");
    assertFalse(version.isValid());
  }
}

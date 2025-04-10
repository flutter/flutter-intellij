/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import org.junit.Test;

import static org.junit.Assert.*;

public class FlutterSdkVersionTest {
  @Test
  public void parsesGoodVersion() {
    final FlutterSdkVersion version = new FlutterSdkVersion("0.0.12");
    assertTrue(version.isValid());
  }

  @Test
  public void trackWidgetCreationRecommendedRange() {
    assertFalse(new FlutterSdkVersion("3.9.0").isSDKSupported());
    assertFalse(new FlutterSdkVersion("3.9.0").isSDKSupported());
    assertFalse(new FlutterSdkVersion("3.9.0.pre").isSDKSupported());
    assertTrue(new FlutterSdkVersion( "3.10.0.pre").isSDKSupported());
    assertTrue(new FlutterSdkVersion( "3.10.1").isSDKSupported());
    assertTrue(new FlutterSdkVersion( "3.10.2").isSDKSupported());
    assertFalse(new FlutterSdkVersion( "unknown").isSDKSupported());
  }

  @Test
  public void handlesBadVersion() {
    final FlutterSdkVersion version = new FlutterSdkVersion("unknown");
    assertFalse(version.isValid());
  }

  @Test
  public void comparesBetaVersions() {
    assertEquals(
      -1,
      new FlutterSdkVersion("1.0.0").compareTo(new FlutterSdkVersion("1.0.1"))
    );
    assertEquals(
      0,
      new FlutterSdkVersion("1.0.0").compareTo(new FlutterSdkVersion("1.0.0"))
    );
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.1").compareTo(new FlutterSdkVersion("1.0.0"))
    );
    // Stable version is ahead of all beta versions with the same major/minor/patch numbers.
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.0").compareTo(new FlutterSdkVersion("1.0.0-1.0.pre"))
    );
    assertEquals(
      -1,
      new FlutterSdkVersion("1.0.0-1.1.pre").compareTo(new FlutterSdkVersion("1.0.0"))
    );
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.0-1.1.pre").compareTo(new FlutterSdkVersion("1.0.0-1.0.pre"))
    );
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.0-2.0.pre").compareTo(new FlutterSdkVersion("1.0.0-1.0.pre"))
    );
    assertEquals(
      -1,
      new FlutterSdkVersion("1.0.0-1.1.pre").compareTo(new FlutterSdkVersion("1.0.0-1.2.pre"))
    );
    assertEquals(
      -1,
      new FlutterSdkVersion("1.0.0-1.1.pre").compareTo(new FlutterSdkVersion("1.0.0-2.1.pre"))
    );
    assertEquals(
      0,
      new FlutterSdkVersion("1.0.0-1.1.pre").compareTo(new FlutterSdkVersion("1.0.0-1.1.pre"))
    );
    assertEquals(
      0,
      new FlutterSdkVersion("1.0.0-1.1.pre.123").compareTo(new FlutterSdkVersion("1.0.0-1.1.pre.123"))
    );
    assertEquals(
      -1,
      new FlutterSdkVersion("1.0.0-1.1.pre.123").compareTo(new FlutterSdkVersion("1.0.0-1.1.pre.124"))
    );
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.0-1.1.pre.124").compareTo(new FlutterSdkVersion("1.0.0-1.1.pre.123"))
    );
    // Master versions will be aware of the latest preceding dev version and have a version number higher than the preceding dev version.
    // e.g. the next commit to master after cutting dev version 2.0.0-2.0.pre would be 2.0.0-3.0.pre.1, with the number 1 signifying 1
    // commit after the previous version.
    assertEquals(
      -1,
      new FlutterSdkVersion("1.0.0-1.1.pre.123").compareTo(new FlutterSdkVersion("1.0.0-1.1.pre"))
    );
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.0-1.1.pre").compareTo(new FlutterSdkVersion("1.0.0-1.1.pre.123"))
    );
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.0-2.0.pre.123").compareTo(new FlutterSdkVersion("1.0.0-1.0.pre"))
    );
    assertEquals(
      1,
      new FlutterSdkVersion("1.0.0-2.0.pre").compareTo(new FlutterSdkVersion("1.0.0-1.0.pre.123"))
    );
  }
}

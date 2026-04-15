/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class FlutterDebugSessionUtilsTest {

  @Test
  public void splitDebugTabNamingHooksAreAvailable() {
    final String error = FlutterDebugSessionUtils.getNamedTabSupportError();
    assertNull(
      "XDebugSessionBuilder lost the reflective hooks needed to label split debug tabs: " + error,
      error
    );
  }
}

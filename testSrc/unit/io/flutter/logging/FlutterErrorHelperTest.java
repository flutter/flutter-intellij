/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlutterErrorHelperTest {
  @Test
  public void testGetAnalyticsId() {
    assertEquals("renderflex-overflowed-by-xxx-pixels-on-the-right",
                 FlutterErrorHelper.getAnalyticsId("A RenderFlex overflowed by 1183 pixels on the right."));
    assertEquals("no-material-widget-found", FlutterErrorHelper.getAnalyticsId("No Material widget found."));
    assertEquals("scaffold.of-called-with-a-context-that-does-not-contain-a-scaffold",
                 FlutterErrorHelper.getAnalyticsId("Scaffold.of() called with a context that does not contain a Scaffold."));
  }
}

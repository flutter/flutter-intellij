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
    assertEquals(
      "a-renderflex-overflowed-by-xxx-pixels-on-the-right",
      FlutterErrorHelper.getAnalyticsId("A RenderFlex overflowed by 1183 pixels on the right."));
    assertEquals(
      "a-renderflex-overflowed-by-xxx-pixels-on-the-right",
      FlutterErrorHelper.getAnalyticsId("A RenderFlex overflowed by 22.3 pixels on the right."));
    assertEquals(
      "no-material-widget-found",
      FlutterErrorHelper.getAnalyticsId("No Material widget found."));
    assertEquals(
      "i-collapse-whitespace",
      FlutterErrorHelper.getAnalyticsId("I collapse  whitespace."));
    assertEquals(
      "scaffold.of-called-with-a-context-that-does-not-contain-a-scaffold",
      FlutterErrorHelper.getAnalyticsId("Scaffold.of() called with a context that does not contain a Scaffold."));
  }

  @Test
  public void testGetAnalyticsId_scrubbing() {
    assertEquals(
      "failed-assertion",
      FlutterErrorHelper.getAnalyticsId(
        "package:flutter/src/widgets/framework.dart':\nfailed assertion: line 123 pos 123: 'owner._debugcurrentbuildtarget == this': is not true"));
    assertEquals(
      "failed-assertion",
      FlutterErrorHelper.getAnalyticsId(
        "'package:flutter/src/widgets/framework.dart':\nfailed assertion: line 123 pos 123: 'owner._debugcurrentbuildtarget == this': is not true"));
    assertEquals(
      "unable-to-load-asset",
      FlutterErrorHelper.getAnalyticsId("unable to load asset: images/foobar.png"));
    assertEquals(
      "all-children-of-this-widget-must-have-a-key",
      FlutterErrorHelper.getAnalyticsId(
        "all children of this widget must have a key.\n'package:flutter/src/material/reorderable_list.dart': failed assertion: line 123 pos xxx: 'children.every((widget w) => w.key != null)'"));
    assertEquals(
      "all-children-of-this-widget-must-have-a-key",
      FlutterErrorHelper.getAnalyticsId(
        "all children of this widget must have a key. 'package:flutter/src/material/reorderable_list.dart': failed assertion: line 123 pos 123: 'children.every((widget w) => w.key != null)'"));
    assertEquals(
      "could-not-find-a-generator-for-route-routesettings-in-the-_widgetsappstate",
      FlutterErrorHelper.getAnalyticsId("could not find a generator for route routesettings(\"/foobar\", null) in the _widgetsappstate"));
    assertEquals(
      "controller's-length-property-does-not-match-the",
      FlutterErrorHelper.getAnalyticsId("controller's length property (123) does not match the\nnumber of tabs"));
  }
}

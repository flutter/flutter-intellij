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
        "'package:flutter/src/widgets/framework.dart':\n" +
        "failed assertion: line 123 pos 123: 'owner._debugcurrentbuildtarget == this': is not true"));
    assertEquals(
      "failed-assertion",
      FlutterErrorHelper.getAnalyticsId(
        "'package:flutter/src/widgets/framework.dart':\n" +
        "failed assertion: line 123 pos 123: 'owner._debugcurrentbuildtarget == this': is not true"));
    assertEquals(
      "unable-to-load-asset",
      FlutterErrorHelper.getAnalyticsId("unable to load asset: images/foobar.png"));
    assertEquals(
      "all-children-of-this-widget-must-have-a-key",
      FlutterErrorHelper.getAnalyticsId(
        "all children of this widget must have a key.\n" +
        "'package:flutter/src/material/reorderable_list.dart': failed assertion: line 123 pos xxx: 'children.every((widget w) => w.key != null)'"));
    assertEquals(
      "all-children-of-this-widget-must-have-a-key",
      FlutterErrorHelper.getAnalyticsId(
        "all children of this widget must have a key.\n" +
        "'package:flutter/src/material/reorderable_list.dart': failed assertion: line 123 pos 123: 'children.every((widget w) => w.key != null)'"));
    assertEquals(
      "could-not-find-a-generator-for-route-routesettings-in-the-_widgetsappstate",
      FlutterErrorHelper.getAnalyticsId("could not find a generator for route routesettings(\"/foobar\", null) in the _widgetsappstate"));
    assertEquals(
      "controller's-length-property-does-not-match-the-number-of-tabs",
      FlutterErrorHelper.getAnalyticsId("controller's length property (123) does not match the number of tabs"));
  }

  @Test
  public void testGetAnalyticsId_errorStudiesApp() {
    assertEquals(
      "renderbox-was-not-laid-out",
      FlutterErrorHelper
        .getAnalyticsId("RenderBox was not laid out: RenderViewport#d878b NEEDS-LAYOUT NEEDS-PAINT NEEDS-COMPOSITING-BITS-UPDATE\n" +
                        "'package:flutter/src/rendering/box.dart':\n" +
                        "Failed assertion: line 1785 pos 12: 'hasSize'"));
    assertEquals(
      "null-check-operator-used-on-a-null-value",
      FlutterErrorHelper.getAnalyticsId("Null check operator used on a null value"));
    assertEquals(
      "a-renderflex-overflowed-by-xxx-pixels-on-the-right",
      FlutterErrorHelper.getAnalyticsId("A RenderFlex overflowed by 13 pixels on the right."));
    assertEquals(
      "no-material-widget-found",
      FlutterErrorHelper.getAnalyticsId("No Material widget found."));
    assertEquals(
      "vertical-viewport-was-given-unbounded-height",
      FlutterErrorHelper.getAnalyticsId("Vertical viewport was given unbounded height."));
    assertEquals(
      "scaffold.of-called-with-a-context-that-does-not-contain-a-scaffold",
      FlutterErrorHelper.getAnalyticsId("Scaffold.of() called with a context that does not contain a Scaffold."));
    assertEquals(
      "exception",
      FlutterErrorHelper.getAnalyticsId("Exception: not today"));
    assertEquals(
      "setstate-or-markneedsbuild-called-during-build",
      FlutterErrorHelper.getAnalyticsId("setState() or markNeedsBuild() called during build."));

    assertEquals(
      "simple-assertion",
      FlutterErrorHelper.getAnalyticsId("simple assertion\n" +
                                        "'package:flutter_error_studies/main.dart':\n" +
                                        "Failed assertion: line 40 pos 12: '1 == 2'"));
    assertEquals(
      "failed-assertion",
      FlutterErrorHelper
        .getAnalyticsId("'package:flutter_error_studies/main.dart': Failed assertion: line 41 pos 12: '1 == 2': is not true."));
    assertEquals(
      "simple-assertion",
      FlutterErrorHelper.getAnalyticsId("simple assertion\n" +
                                        "'package:flutter_error_studies/main.dart':\n" +
                                        "Failed assertion: line 42 pos 12: '() {\n" +
                                        "      return 1 == 2;\n" +
                                        "    }()'"));
    assertEquals(
      "failed-assertion",
      FlutterErrorHelper.getAnalyticsId("'package:flutter_error_studies/main.dart': Failed assertion: line 45 pos 12: '() {\n" +
                                        "      return 1 == 2;\n" +
                                        "    }()': is not true."));
  }
}

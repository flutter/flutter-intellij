/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.teamdev.jxbrowser.zoom.ZoomLevel;
import io.flutter.utils.ZoomLevelSelector;
import org.junit.Test;

import static io.flutter.FlutterUtils.isValidDartIdentifier;
import static io.flutter.FlutterUtils.isValidPackageName;
import static org.junit.Assert.*;

public class FlutterUtilsTest {

  @Test
  public void validIdentifier() {
    final String[] validIds = {"a", "_", "abc", "_abc", "a_bc", "abc$", "$", "$$", "$_$"};
    for (String id : validIds) {
      assertTrue("expected " + id + " to be valid", isValidDartIdentifier(id));
    }

    final String[] invalidIds = {"1", "1a", "a-bc", "a.b"};
    for (String id : invalidIds) {
      assertFalse("expected " + id + " to be invalid", isValidDartIdentifier(id));
    }
  }

  @Test
  public void validPackageNames() {
    final String[] validNames = {"a", "a_b_c", "abc", "a_long_module_name_that_is_legal"};
    for (String name : validNames) {
      assertTrue("expected " + name + " to be valid", isValidPackageName(name));
    }

    final String[] invalidNames = {"_", "_a", "a_", "A", "Abc", "A_bc", "a_long_module_name_that_is_illegal_"};
    for (String name : invalidNames) {
      assertFalse("expected " + name + " to be invalid", isValidPackageName(name));
    }
  }

  @Test
  public void zoomLevelSelector() {
    final ZoomLevelSelector zoomLevelSelector = new ZoomLevelSelector();
    assertSame(zoomLevelSelector.getClosestZoomLevel(-70), ZoomLevel.P_25);
    assertSame(zoomLevelSelector.getClosestZoomLevel(-10), ZoomLevel.P_25);
    assertSame(zoomLevelSelector.getClosestZoomLevel(0), ZoomLevel.P_25);
    assertSame(zoomLevelSelector.getClosestZoomLevel(1), ZoomLevel.P_25);
    assertSame(zoomLevelSelector.getClosestZoomLevel(20), ZoomLevel.P_25);
    assertSame(zoomLevelSelector.getClosestZoomLevel(28), ZoomLevel.P_25);
    assertSame(zoomLevelSelector.getClosestZoomLevel(35), ZoomLevel.P_33);
    assertSame(zoomLevelSelector.getClosestZoomLevel(222), ZoomLevel.P_200);
    assertSame(zoomLevelSelector.getClosestZoomLevel(226), ZoomLevel.P_250);
    assertSame(zoomLevelSelector.getClosestZoomLevel(700), ZoomLevel.P_500);
  }
}

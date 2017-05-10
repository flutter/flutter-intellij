/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import org.junit.Test;

import static io.flutter.FlutterUtils.isValidDartIdentifier;
import static io.flutter.FlutterUtils.isValidPackageName;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    final String[] validNames = {"a", "a_b_c", "abc"};
    for (String name : validNames) {
      assertTrue("expected " + name + " to be valid", isValidPackageName(name));
    }

    final String[] invalidNames = {"_", "_a", "a_", "A", "Abc", "A_bc"};
    for (String name : invalidNames) {
      assertFalse("expected " + name + " to be invalid", isValidPackageName(name));
    }
  }
}

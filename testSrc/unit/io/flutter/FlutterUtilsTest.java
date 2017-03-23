/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import junit.framework.TestCase;

public class FlutterUtilsTest extends TestCase {

  public void testValidIdentifier() {
    final String[] validIds = {"a", "_", "abc", "_abc", "a_bc", "abc$", "$", "$$", "$_$"};
    for (String id : validIds) {
      assertTrue("expected " + id + " to be valid", FlutterUtils.isValidDartdentifier(id));
    }

    final String[] invalidIds = {"1", "1a", "a-bc", "a.b"};
    for (String id : invalidIds) {
      assertFalse("expected " + id + " to be invalid", FlutterUtils.isValidDartdentifier(id));
    }
  }
}

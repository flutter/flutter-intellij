/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.util.system.OS;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SystemUtils}.
 */
public class SystemUtilsTest {

  @Test
  public void getOsNameAndVersion_includesCurrentOsNameAndSystemVersion() {
    final String expectedVersion = System.getProperty("os.version", "unknown");
    final String actual = SystemUtils.getOsNameAndVersion();

    assertFalse(actual.isEmpty());
    assertTrue(actual.startsWith(OS.CURRENT.name() + " "));
    assertEquals(OS.CURRENT.name() + " " + expectedVersion, actual);
  }

  @Test
  public void getOsNameAndVersion_fallsBackToUnknownWhenPropertyCleared() {
    final String originalVersion = System.getProperty("os.version");
    try {
      System.clearProperty("os.version");
      assertEquals(OS.CURRENT.name() + " unknown", SystemUtils.getOsNameAndVersion());
    }
    finally {
      if (originalVersion != null) {
        System.setProperty("os.version", originalVersion);
      }
    }
  }
}

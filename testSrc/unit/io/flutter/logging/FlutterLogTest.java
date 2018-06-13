/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import io.flutter.logging.FlutterLog.Level;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlutterLogTest {

  @Test
  public void testLevelForValue() {
    assertEquals(Level.NONE, Level.forValue(0));
    assertEquals(Level.FINEST, Level.forValue(Level.FINEST.value));
    assertEquals(Level.FINEST, Level.forValue(Level.FINEST.value + 1));
    assertEquals(Level.FINER, Level.forValue(Level.FINER.value));
    assertEquals(Level.SHOUT, Level.forValue(Level.SHOUT.value));
    assertEquals(Level.SHOUT, Level.forValue(Level.SHOUT.value + 1));
  }
}

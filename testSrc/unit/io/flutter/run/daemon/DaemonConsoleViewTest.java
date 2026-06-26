/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DaemonConsoleViewTest {

  @Test
  public void isAtScrollEnd_returnsTrueWhenScrolledToBottom() {
    // scrollOffset(900) + visibleHeight(100) == totalHeight(1000)
    assertTrue(DaemonConsoleView.isAtScrollEnd(900, 100, 1000, 15));
  }

  @Test
  public void isAtScrollEnd_returnsTrueWhenWithinOneLineOfBottom() {
    // One line above the very bottom — treated as "at end" to avoid jitter.
    assertTrue(DaemonConsoleView.isAtScrollEnd(890, 100, 1000, 15));
  }

  @Test
  public void isAtScrollEnd_returnsFalseWhenScrolledToTop() {
    assertFalse(DaemonConsoleView.isAtScrollEnd(0, 100, 1000, 15));
  }

  @Test
  public void isAtScrollEnd_returnsFalseWhenInMiddle() {
    assertFalse(DaemonConsoleView.isAtScrollEnd(400, 100, 1000, 15));
  }

  @Test
  public void isAtScrollEnd_returnsTrueWhenContentShorterThanViewport() {
    // Nothing to scroll — treat as "at end" so normal output still auto-scrolls.
    assertTrue(DaemonConsoleView.isAtScrollEnd(0, 500, 200, 15));
  }
}

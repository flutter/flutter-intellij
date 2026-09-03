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

  @Test
  public void isErrorStart_trueForErrorContentType() {
    // Anything routed to the error stream qualifies regardless of its text.
    assertTrue(DaemonConsoleView.isErrorStart("Finished with error: exited abnormally", true));
  }

  @Test
  public void isErrorStart_trueForFlutterExceptionBanner() {
    // Framework exceptions are printed to stdout (not the error stream), so we match the
    // banner text instead.
    assertTrue(DaemonConsoleView.isErrorStart(
      "======== Exception caught by rendering library =====================================", false));
  }

  @Test
  public void isErrorStart_matchesBannerCaseInsensitively() {
    assertTrue(DaemonConsoleView.isErrorStart("══╡ EXCEPTION CAUGHT BY WIDGETS LIBRARY ╞══", false));
  }

  @Test
  public void isErrorStart_trueForAnotherExceptionBanner() {
    assertTrue(DaemonConsoleView.isErrorStart("Another exception was thrown: Bad state", false));
  }

  @Test
  public void isErrorStart_falseForOrdinaryStdout() {
    // Ordinary log lines must not trigger a scroll jump.
    assertFalse(DaemonConsoleView.isErrorStart("Reloaded 1 of 512 libraries in 240ms.", false));
  }

  @Test
  public void isErrorStart_falseForStackFrameLine() {
    // A line from within a stack trace is not itself the start of an error.
    assertFalse(DaemonConsoleView.isErrorStart("#0      RenderViewport.performResize (package:flutter/...)", false));
  }

  @Test
  public void shouldReleasePin_falseForTransientAtEndRightAfterPinning() {
    // Immediately after pinning, our own scroll can look "at end" on a short document;
    // the guard must keep the pin so we do not jump to a later error.
    assertFalse(DaemonConsoleView.shouldReleasePin(true, true));
  }

  @Test
  public void shouldReleasePin_falseWhileViewportIsAwayFromEnd() {
    assertFalse(DaemonConsoleView.shouldReleasePin(false, false));
  }

  @Test
  public void shouldReleasePin_trueWhenUserReturnsToEndAfterScrollingAway() {
    // The guard has cleared (viewport moved away), and now the user is back at the bottom.
    assertTrue(DaemonConsoleView.shouldReleasePin(true, false));
  }
}

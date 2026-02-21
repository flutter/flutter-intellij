/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SlidingWindowStatsTest {
  @Test
  public void simpleSlidingWindowStats() {
    final SlidingWindowStats stats = new SlidingWindowStats();
    assertEquals(0, stats.getTotal());
    stats.add(1, 0);
    stats.add(1, 1);
    assertEquals(2, stats.getTotal());
    stats.add(1, 2);
    stats.add(1, 3);
    assertEquals(4, stats.getTotal());
    assertEquals(4, stats.getTotalWithinWindow(0));
    assertEquals(3, stats.getTotalWithinWindow(1));
    assertEquals(2, stats.getTotalWithinWindow(2));
    assertEquals(1, stats.getTotalWithinWindow(3));
  }

  @Test
  public void totalSinceNavigation() {
    final SlidingWindowStats stats = new SlidingWindowStats();
    assertEquals(0, stats.getTotal());
    stats.add(1, 0);
    stats.add(1, 1);
    assertEquals(2, stats.getTotalSinceNavigation());
    assertEquals(2, stats.getTotal());
    stats.onNavigation();
    assertEquals(0, stats.getTotalSinceNavigation());
    assertEquals(2, stats.getTotal());
    stats.add(1, 2);
    assertEquals(1, stats.getTotalSinceNavigation());
  }

  private void add1000Times(SlidingWindowStats stats, int timeStamp) {
    for (int i = 0; i < 1000; i++) {
      stats.add(1, timeStamp);
    }
  }

  @Test
  public void duplicateSlidingWindowStatTimestamps() {
    final SlidingWindowStats stats = new SlidingWindowStats();

    assertEquals(0, stats.getTotal());
    add1000Times(stats, 0);
    add1000Times(stats, 1);
    assertEquals(2000, stats.getTotal());
    add1000Times(stats, 2);
    add1000Times(stats, 3);
    assertEquals(4000, stats.getTotal());
    assertEquals(4000, stats.getTotalWithinWindow(0));
    assertEquals(3000, stats.getTotalWithinWindow(1));
    assertEquals(2000, stats.getTotalWithinWindow(2));
    assertEquals(1000, stats.getTotalWithinWindow(3));
    add1000Times(stats, 4);
    add1000Times(stats, 5);
    add1000Times(stats, 6);
    add1000Times(stats, 7);
    add1000Times(stats, 8);
    add1000Times(stats, 9);
    add1000Times(stats, 10);
    assertEquals(11000, stats.getTotal());
    assertEquals(11000, stats.getTotalWithinWindow(0));
    add1000Times(stats, 11);
  }

  @Test
  public void clearSlidingWindowStats() {
    final SlidingWindowStats stats = new SlidingWindowStats();
    stats.add(1, 0);
    stats.add(1, 1);
    stats.add(1, 2);
    stats.add(1, 3);
    assertEquals(4, stats.getTotal());
    assertEquals(4, stats.getTotalWithinWindow(0));

    stats.clear();
    stats.add(1, 4);
    stats.add(1, 5);
    assertEquals(2, stats.getTotal());
    assertEquals(1, stats.getTotalWithinWindow(5));
    assertEquals(2, stats.getTotalWithinWindow(0));
    stats.clear();

    // Intentionally shift timestamps backwards as could happen after a hot
    // reload.
    stats.add(1, 0);
    stats.add(1, 1);
    stats.add(1, 2);
    stats.add(1, 3);
    assertEquals(4, stats.getTotal());
    assertEquals(4, stats.getTotalWithinWindow(0));
    stats.add(1, 4);
    stats.add(1, 5);
    stats.add(1, 6);
    stats.add(1, 7);
    stats.add(1, 8);
    stats.add(1, 9);
    stats.add(1, 10);
    assertEquals(11, stats.getTotal());
    assertEquals(11, stats.getTotalWithinWindow(0));
  }
}

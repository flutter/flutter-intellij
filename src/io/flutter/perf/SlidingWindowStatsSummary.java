/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

/**
 * Snapshot of a SlidingWindowStats object for a specific time.
 */
public class SlidingWindowStatsSummary {
  final int total;
  final int pastSecond;
  final int totalSinceNavigation;
  private final Location location;

  SlidingWindowStatsSummary(SlidingWindowStats stats, int currentTime, Location location) {
    total = stats.getTotal();
    pastSecond = stats.getTotalWithinWindow(currentTime - 999);
    totalSinceNavigation = stats.getTotalSinceNavigation();
    this.location = location;
  }

  public int getTotal() {
    return total;
  }

  public int getPastSecond() {
    return pastSecond;
  }

  public int getTotalSinceNavigation() {
    return totalSinceNavigation;
  }

  public Location getLocation() {
    return location;
  }
}

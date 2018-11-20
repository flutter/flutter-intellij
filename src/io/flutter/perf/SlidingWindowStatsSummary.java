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
  final int[] cachedStats;
  private final Location location;

  public SlidingWindowStatsSummary(SlidingWindowStats stats, int currentTime, Location location) {
    cachedStats = new int[PerfMetric.values().length];
    for (PerfMetric metric : PerfMetric.values()) {
      cachedStats[metric.ordinal()] = stats.getValue(metric, currentTime);
    }
    this.location = location;
  }

  public Location getLocation() {
    return location;
  }

  public int getValue(PerfMetric metric) {
    return cachedStats[metric.ordinal()];
  }
}

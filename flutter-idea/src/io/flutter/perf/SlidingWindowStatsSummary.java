/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import org.jetbrains.annotations.NotNull;

/**
 * Snapshot of a SlidingWindowStats object for a specific time.
 */
public class SlidingWindowStatsSummary {
  private final int[] cachedStats;
  private final @NotNull Location location;

  public SlidingWindowStatsSummary(@NotNull SlidingWindowStats stats, int currentTime, @NotNull Location location) {
    cachedStats = new int[PerfMetric.values().length];
    for (PerfMetric metric : PerfMetric.values()) {
      cachedStats[metric.ordinal()] = stats.getValue(metric, currentTime);
    }
    this.location = location;
  }

  public @NotNull
  Location getLocation() {
    return location;
  }

  public int getValue(PerfMetric metric) {
    return cachedStats[metric.ordinal()];
  }
}

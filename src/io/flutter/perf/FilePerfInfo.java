/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.util.TextRange;

import java.util.Arrays;
import java.util.Collection;

/**
 * Performance stats for a single source file.
 * <p>
 * Typically the TextRange objects correspond to widget constructor locations.
 * A single constructor location may have multiple SummaryStats objects one for
 * each kind of stats (Widget repaints, rebuilds, etc).
 */
class FilePerfInfo {
  private final Multimap<TextRange, SummaryStats> stats = LinkedListMultimap.create();
  long maxTimestamp = -1;
  private final int[] totalForMetric = new int[PerfMetric.values().length];

  public void clear() {
    stats.clear();
    maxTimestamp = -1;
    Arrays.fill(totalForMetric, 0);
  }

  public Iterable<TextRange> getLocations() {
    return stats.keySet();
  }

  public Iterable<SummaryStats> getStats() {
    return stats.values();
  }

  public boolean hasLocation(TextRange range) {
    return stats.containsKey(range);
  }

  public int getTotalValue(PerfMetric metric) {
    return totalForMetric[metric.ordinal()];
  }

  public int getValue(TextRange range, PerfMetric metric) {
    final Collection<SummaryStats> entries = stats.get(range);
    if (entries == null) {
      return 0;
    }
    int count = 0;
    for (SummaryStats entry : entries) {
      count += entry.getValue(metric);
    }
    return count;
  }

  Iterable<SummaryStats> getRangeStats(TextRange range) {
    return stats.get(range);
  }

  public long getMaxTimestamp() {
    return maxTimestamp;
  }

  public void add(TextRange range, SummaryStats entry) {
    stats.put(range, entry);
    for (PerfMetric metric : PerfMetric.values()) {
      totalForMetric[metric.ordinal()] += entry.getValue(metric);
    }
  }

  public void markAppIdle() {
    for (PerfMetric metric : PerfMetric.values()) {
      if (metric.timeIntervalMetric) {
        totalForMetric[metric.ordinal()] = 0;
      }
    }

    for (SummaryStats stats : stats.values()) {
      stats.markAppIdle();
    }
  }

  public int getCurrentValue(TextRange range) {
    return getValue(range, PerfMetric.peakRecent);
  }
}

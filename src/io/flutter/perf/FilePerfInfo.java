/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.util.TextRange;

import java.util.Collection;

/**
 * Statistics summarizing how frequently an event as occurred overall and in
 * the past second.
 */
class SummaryStats {
  private final PerfReportKind kind;
  private final int total;
  private final String description;
  private int pastSecond = 0;

  SummaryStats(PerfReportKind kind, int total, int pastSecond, String description) {
    this.kind = kind;
    this.total = total;
    this.pastSecond = pastSecond;
    this.description = description;
  }

  PerfReportKind getKind() {
    return kind;
  }

  int getTotal() {
    return total;
  }

  int getPastSecond() {
    return pastSecond;
  }

  public void markAppIdle() {
    pastSecond = 0;
  }

  public String getDescription() {
    return description;
  }
}

/**
 * Performance stats for a single source file.
 *
 * Typically the TextRange objects correspond to widget constructor locations.
 * A single constructor location may have multiple SummaryStats objects one for
 * each kind of stats (Widget repaints, rebuilds, etc).
 */
class FilePerfInfo {
  private final Multimap<TextRange, SummaryStats> stats = LinkedListMultimap.create();
  long maxTimestamp = -1;
  private int totalPastSecond = 0;

  public void clear() {
    stats.clear();
    maxTimestamp = -1;
    totalPastSecond = 0;
  }

  public Iterable<TextRange> getLocations() {
    return stats.keySet();
  }

  public boolean hasLocation(TextRange range) {
    return stats.containsKey(range);
  }

  public int getCountPastSecond() {
    return totalPastSecond;
  }

  public int getCountPastSecond(TextRange range) {
    final Collection<SummaryStats> entries = stats.get(range);
    if (entries == null) {
      return 0;
    }
    int count = 0;
    for (SummaryStats entry : entries) {
      count += entry.getPastSecond();
    }
    return count;
  }

  public int getTotalCount(TextRange range) {
    final Collection<SummaryStats> entries = stats.get(range);
    if (entries == null) {
      return 0;
    }
    int count = 0;
    for (SummaryStats entry : entries) {
      count += entry.getTotal();
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
    totalPastSecond += entry.getPastSecond();
  }

  public void markAppIdle() {
    totalPastSecond = 0;
    for (SummaryStats stats : stats.values()) {
      stats.markAppIdle();
    }
  }
}

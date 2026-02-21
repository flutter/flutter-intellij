/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

/**
 * Statistics summarizing how frequently an event has occurred overall and in
 * the past second.
 */
public class SummaryStats {
  private final PerfReportKind kind;
  private final SlidingWindowStatsSummary entry;
  private final String description;
  boolean active = true;

  SummaryStats(PerfReportKind kind, SlidingWindowStatsSummary entry, String description) {
    this.kind = kind;
    this.entry = entry;
    this.description = description;
  }

  public PerfReportKind getKind() {
    return kind;
  }

  public int getValue(PerfMetric metric) {
    if (metric.timeIntervalMetric && !active) {
      return 0;
    }
    return entry.getValue(metric);
  }

  public void markAppIdle() {
    active = false;
  }

  public String getDescription() {
    return description;
  }

  public Location getLocation() {
    return entry.getLocation();
  }
}

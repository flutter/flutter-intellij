/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

/**
 * Performance metrics.
 * <p>
 * Additional performance metrics can be defined without requiring changes to
 * package:flutter as computation of metrics is performed in Java using
 * the SlidingWindowStats class.
 */
public enum PerfMetric {
  lastFrame("Last Frame", true),
  peakRecent("Peak Recent", true),
  pastSecond("Past Second", true),
  totalSinceEnteringCurrentScreen("Current Screen", false),
  total("Total", false);

  public final String name;
  public final boolean timeIntervalMetric;

  PerfMetric(String name, boolean timeIntervalMetric) {
    this.name = name;
    this.timeIntervalMetric = timeIntervalMetric;
  }

  @Override
  public String toString() {
    return name;
  }
}

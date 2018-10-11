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
  total("total"),
  totalSinceRouteChange("totalSinceRouteChange"),
  lastSecond("lastSecond");
  public final String name;

  PerfMetric(String name) {
    this.name = name;
  }
}

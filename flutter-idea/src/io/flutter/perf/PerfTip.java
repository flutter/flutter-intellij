/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import java.util.List;

/**
 * A performance tip describing a suggestion to optimize a Flutter application.
 */
public class PerfTip {
  final PerfTipRule rule;
  final List<Location> locations;
  double confidence;

  PerfTip(PerfTipRule rule, List<Location> locations, double confidence) {
    this.rule = rule;
    this.locations = locations;
    this.confidence = confidence;
  }

  public PerfTipRule getRule() {
    return rule;
  }

  public String getMessage() {
    return rule.getMessage();
  }

  /**
   * Locations within the application that called the tip to trigger.
   */
  public List<Location> getLocations() {
    return locations;
  }

  /**
   * Confidence between zero and 1 that the rule should be applied.
   */
  public double getConfidence() {
    return confidence;
  }

  public String getUrl() {
    return rule.getUrl();
  }
}

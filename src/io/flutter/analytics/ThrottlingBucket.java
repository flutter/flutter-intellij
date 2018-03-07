/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

/**
 * A throttling algorithm.
 * <p/>
 * This models the throttling after a bucket with water dripping into it at the rate of 1 drop per
 * second. If the bucket has water when an operation is requested, 1 drop of water is removed and
 * the operation is performed. If not the operation is skipped. This algorithm lets operations
 * be performed in bursts without throttling, but holds the overall average rate of operations to 1
 * per second.
 */
public class ThrottlingBucket {
  private final int startingCount;
  private int drops;
  private long lastReplenish;

  public ThrottlingBucket(final int startingCount) {
    this.startingCount = startingCount;
    this.drops = startingCount;
    this.lastReplenish = System.currentTimeMillis();
  }

  public boolean removeDrop() {
    checkReplenish();

    if (drops <= 0) {
      return false;
    }
    else {
      drops--;
      return true;
    }
  }

  private void checkReplenish() {
    final long now = System.currentTimeMillis();

    if (lastReplenish + 1000L >= now) {
      final int inc = ((int)(now - lastReplenish)) / 1000;
      drops = Math.min(drops + inc, startingCount);
      lastReplenish += (1000L * inc);
    }
  }
}

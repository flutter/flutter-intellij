/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

/**
 * Note: this class is a simplified version of one available in the Apache library. We use this one
 * instead in order to avoid a dependency.
 */
public class StopWatch {
  private long startTime;
  private long stopTime;

  public StopWatch() {

  }

  public void start() {
    startTime = System.currentTimeMillis();
  }

  public void stop() {
    stopTime = System.currentTimeMillis();
  }

  public long getTimeMillis() {
    return stopTime - startTime;
  }
}

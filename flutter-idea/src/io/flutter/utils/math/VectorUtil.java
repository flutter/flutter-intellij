/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

/**
 * This code is ported from the Dart vector_math package.
 */
public class VectorUtil {
  public static double clamp(double x, double min, double max) {
    return Math.min(Math.max(x, min), max);
  }
}


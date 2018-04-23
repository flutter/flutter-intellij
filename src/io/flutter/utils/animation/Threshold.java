/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.animation;

/**
 * A curve that is 0.0 until it hits the threshold, then it jumps to 1.0.
 * <p>
 * https://flutter.github.io/assets-for-api-docs/animation/curve_threshold.png
 */
public class Threshold extends Curve {
  /// Creates a threshold curve.
  public Threshold(double threshold) {
    this.threshold = threshold;
  }

  /// The value before which the curve is 0.0 and after which the curve is 1.0.
  ///
  /// When t is exactly [threshold], the curve has the value 1.0.
  final double threshold;

  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    assert (threshold >= 0.0);
    assert (threshold <= 1.0);
    if (t == 0.0 || t == 1.0) {
      return t;
    }
    return t < threshold ? 0.0 : 1.0;
  }
}

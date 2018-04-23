/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.animation;

import io.flutter.utils.animation.Curve;

/**
 * A curve that is the reversed inversion of its given curve.
 * <p>
 * This curve evaluates the given curve in reverse (i.e., from 1.0 to 0.0 as t
 * increases from 0.0 to 1.0) and returns the inverse of the given curve's value
 * (i.e., 1.0 minus the given curve's value).
 * <p>
 * This is the class used to implement the getFlipped method on curves.
 */
public class FlippedCurve extends Curve {
  /**
   * Creates a flipped curve.
   */
  public FlippedCurve(Curve curve) {
    assert (curve != null);
    this.curve = curve;
  }

  /**
   * The curve that is being flipped.
   */
  public final Curve curve;

  @Override
  public double transform(double t) {
    return 1.0 - curve.transform(1.0 - t);
  }

  @Override
  public String toString() {
    return this.getClass().toString() + "(" + curve + ")";
  }
}

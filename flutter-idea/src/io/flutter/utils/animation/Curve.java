/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.animation;

/**
 * A mapping of the unit interval to the unit interval.
 * <p>
 * A curve must map t=0.0 to 0.0 and t=1.0 to 1.0.
 */
public abstract class Curve {
  /// Returns the value of the curve at point `t`.
  ///
  /// The value of `t` must be between 0.0 and 1.0, inclusive. Subclasses should
  /// assert that this is true.
  ///
  /// A curve must map t=0.0 to 0.0 and t=1.0 to 1.0.
  public abstract double transform(double t);

  public double interpolate(double start, double end, double t) {
    final double fraction = transform(t);
    return start * (1 - fraction) + end * fraction;
  }

  public int interpolate(int start, int end, double t) {
    return (int)Math.round(interpolate((double)start, (double)end, t));
  }

  /// Returns a new curve that is the reversed inversion of this one.
  /// This is often useful as the reverseCurve of an [Animation].
  ///
  /// ![](https://flutter.github.io/assets-for-api-docs/animation/curve_bounce_in.png)
  /// ![](https://flutter.github.io/assets-for-api-docs/animation/curve_flipped.png)
  ///
  /// See also:
  ///
  ///  * [FlippedCurve], the class that is used to implement this getter.
  public Curve getFlipped() {
    return new FlippedCurve(this);
  }

  @Override
  public String toString() {
    return getClass().toString();
  }
}
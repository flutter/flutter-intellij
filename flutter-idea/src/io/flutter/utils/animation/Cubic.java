/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.animation;

/// A cubic polynomial mapping of the unit interval.
///
/// The Curves class contains some commonly used cubic curves:
///
///  * Curves.EASE
///  * Curves.EASE_IN
///  * Curves.EASE_OUT
///  * Curves.EASE_IN_OUT
///
/// https://flutter.github.io/assets-for-api-docs/animation/curve_ease.png
/// https://flutter.github.io/assets-for-api-docs/animation/curve_ease_in.png
/// https://flutter.github.io/assets-for-api-docs/animation/curve_ease_out.png
/// https://flutter.github.io/assets-for-api-docs/animation/curve_ease_in_out.png
///
/// The Cubic class implements third-order Bézier curves.
class Cubic extends Curve {
  /// Creates a cubic curve.
  ///
  /// Rather than creating a new instance, consider using one of the common
  /// cubic curves in Curves.
  Cubic(double a, double b, double c, double d) {
    this.a = a;
    this.b = b;
    this.c = c;
    this.d = d;
  }

  /// The x coordinate of the first control point.
  ///
  /// The line through the point (0, 0) and the first control point is tangent
  /// to the curve at the point (0, 0).
  final double a;

  /// The y coordinate of the first control point.
  ///
  /// The line through the point (0, 0) and the first control point is tangent
  /// to the curve at the point (0, 0).
  final double b;

  /// The x coordinate of the second control point.
  ///
  /// The line through the point (1, 1) and the second control point is tangent
  /// to the curve at the point (1, 1).
  final double c;

  /// The y coordinate of the second control point.
  ///
  /// The line through the point (1, 1) and the second control point is tangent
  /// to the curve at the point (1, 1).
  final double d;

  static final double _kCubicErrorBound = 0.001;

  double evaluateCubic(double a, double b, double m) {
    return 3 * a * (1 - m) * (1 - m) * m +
           3 * b * (1 - m) * m * m +
           m * m * m;
  }

  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    double start = 0.0;
    double end = 1.0;
    while (true) {
      final double midpoint = (start + end) / 2;
      final double estimate = evaluateCubic(a, c, midpoint);
      if (Math.abs(t - estimate) < _kCubicErrorBound) {
        return evaluateCubic(b, d, midpoint);
      }
      if (estimate < t) {
        start = midpoint;
      }
      else {
        end = midpoint;
      }
    }
  }

  @Override
  public String toString() {
    return getClass().toString() + "(" + a + ", " + b + ", " + c + ", " + d + ")";
  }
}

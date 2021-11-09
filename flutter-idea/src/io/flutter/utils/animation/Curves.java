/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.animation;

/**
 * A collection of common animation curves.
 * <p>
 * https://flutter.github.io/assets-for-api-docs/animation/curve_bounce_in.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_bounce_in_out.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_bounce_out.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_decelerate.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_ease.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_ease_in.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_ease_in_out.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_ease_out.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_in.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_in_out.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_out.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_fast_out_slow_in.png
 * https://flutter.github.io/assets-for-api-docs/animation/curve_linear.png
 * <p>
 * See also:
 * <p>
 * * Curve, the interface implemented by the constants available from the
 * Curves class.
 */
public class Curves {
  /**
   * A LINEAR animation curve.
   * <p>
   * This is the identity map over the unit interval: its Curve.transform
   * method returns its input unmodified. This is useful as a default curve for
   * cases where a Curve is required but no actual curve is desired.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_linear.png
   */
  public static final Curve LINEAR = new Linear();

  /**
   * A curve where the rate of change starts out quickly and then decelerates; an
   * upside-down `f(t) = t²` parabola.
   * <p>
   * This is equivalent to the Android `DecelerateInterpolator` class with a unit
   * factor (the default factor).
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_decelerate.png
   */
  public static final Curve DECELERATE = new DecelerateCurve();

  /**
   * A cubic animation curve that speeds up quickly and ends slowly.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_ease.png
   */
  public static final Cubic EASE = new Cubic(0.25, 0.1, 0.25, 1.0);

  /**
   * A cubic animation curve that starts slowly and ends quickly.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_ease_in.png
   */
  public static final Cubic EASE_IN = new Cubic(0.42, 0.0, 1.0, 1.0);

  /**
   * A cubic animation curve that starts quickly and ends slowly.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_ease_out.png
   */
  public static final Cubic EASE_OUT = new Cubic(0.0, 0.0, 0.58, 1.0);

  /**
   * A cubic animation curve that starts slowly, speeds up, and then and ends slowly.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_ease_in_out.png
   */
  public static final Cubic EASE_IN_OUT = new Cubic(0.42, 0.0, 0.58, 1.0);

  /**
   * A curve that starts quickly and eases into its final position.
   * <p>
   * Over the course of the animation, the object spends more time near its
   * final destination. As a result, the user isn’t left waiting for the
   * animation to finish, and the negative effects of motion are minimized.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_fast_out_slow_in.png
   */
  public static final Cubic FAST_OUT_SLOW_IN = new Cubic(0.4, 0.0, 0.2, 1.0);

  /**
   * An oscillating curve that grows in magnitude.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_bounce_in.png
   */
  public static final Curve BOUNCE_IN = new BounceInCurve();

  /**
   * An oscillating curve that first grows and then shrink in magnitude.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_bounce_out.png
   */
  public static final Curve BOUNCE_OUT = new BounceOutCurve();

  /**
   * An oscillating curve that first grows and then shrink in magnitude.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_bounce_in_out.png
   */
  public static final Curve BOUNCE_IN_OUT = new BounceInOutCurve();

  /**
   * An oscillating curve that grows in magnitude while overshooting its bounds.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_in.png
   */
  public static final ElasticInCurve ELASTIC_IN = new ElasticInCurve();

  /**
   * An oscillating curve that shrinks in magnitude while overshooting its bounds.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_out.png
   */
  public static final ElasticOutCurve ELASTIC_OUT = new ElasticOutCurve();

  /**
   * An oscillating curve that grows and then shrinks in magnitude while overshooting its bounds.
   * <p>
   * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_in_out.png
   */
  public static final ElasticInOutCurve ELASTIC_IN_OUT = new ElasticInOutCurve();
}

/**
 * A curve where the rate of change starts out quickly and then decelerates; an
 * upside-down `f(t) = t²` parabola.
 * <p>
 * This is equivalent to the Android `DecelerateInterpolator` class with a unit
 * factor (the default factor).
 * <p>
 * See Curves.DECELERATE for an instance of this class.
 */
class DecelerateCurve extends Curve {

  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    // Intended to match the behavior of:
    // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/animation/DecelerateInterpolator.java
    // ...as of December 2016.
    t = 1.0 - t;
    return 1.0 - t * t;
  }
}

/**
 * The identity map over the unit interval.
 * <p>
 * See Curves.LINEAR for an instance of this class.
 */
class Linear extends Curve {
  @Override
  public double transform(double t) {
    return t;
  }
}


/**
 * An oscillating curve that grows in magnitude.
 * <p>
 * See Curves.BOUNCE_IN for an instance of this class.
 */
class BounceInCurve extends Curve {
  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    return 1.0 - BounceInOutCurve._bounce(1.0 - t);
  }
}

/**
 * An oscillating curve that shrink in magnitude.
 * <p>
 * See Curves.BOUNCE_OUT for an instance of this class.
 */
class BounceOutCurve extends Curve {
  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    return BounceInOutCurve._bounce(t);
  }
}

/**
 * An oscillating curve that first grows and then shrink in magnitude.
 * <p>
 * See Curves.BOUNCE_IN_OUT for an instance of this class.
 */
class BounceInOutCurve extends Curve {

  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    if (t < 0.5) {
      return (1.0 - _bounce(1.0 - t)) * 0.5;
    }
    else {
      return _bounce(t * 2.0 - 1.0) * 0.5 + 0.5;
    }
  }

  static double _bounce(double t) {
    if (t < 1.0 / 2.75) {
      return 7.5625 * t * t;
    }
    else if (t < 2 / 2.75) {
      t -= 1.5 / 2.75;
      return 7.5625 * t * t + 0.75;
    }
    else if (t < 2.5 / 2.75) {
      t -= 2.25 / 2.75;
      return 7.5625 * t * t + 0.9375;
    }
    t -= 2.625 / 2.75;
    return 7.5625 * t * t + 0.984375;
  }
}

/**
 * An oscillating curve that grows in magnitude while overshooting its bounds.
 * <p>
 * An instance of this class using the default period of 0.4 is available as
 * Curves.ELASTIC_IN.
 * <p>
 * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_in.png
 */
class ElasticInCurve extends Curve {
  /**
   * Creates an elastic-in curve.
   * <p>
   * Rather than creating a new instance, consider using Curves.ELASTIC_IN.
   */
  public ElasticInCurve() {
    this(0.4);
  }

  public ElasticInCurve(double period) {
    this.period = period;
  }

  /**
   * The duration of the oscillation.
   */
  final double period;

  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    final double s = period / 4.0;
    t = t - 1.0;
    return -Math.pow(2.0, 10.0 * t) * Math.sin((t - s) * (Math.PI * 2.0) / period);
  }

  @Override
  public String toString() {
    return getClass().toString() + "(" + period + ")";
  }
}

/**
 * An oscillating curve that shrinks in magnitude while overshooting its bounds.
 * <p>
 * An instance of this class using the default period of 0.4 is available as
 * Curves.ELASTIC_OUT.
 * <p>
 * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_out.png
 */
class ElasticOutCurve extends Curve {
  /**
   * Creates an elastic-out curve.
   * <p>
   * Rather than creating a new instance, consider using Curves.ELASTIC_OUT.
   */
  public ElasticOutCurve() {
    this(0.4);
  }

  public ElasticOutCurve(double period) {
    this.period = period;
  }

  /**
   * The duration of the oscillation.
   */
  final double period;

  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    final double s = period / 4.0;
    return Math.pow(2.0, -10 * t) * Math.sin((t - s) * (Math.PI * 2.0) / period) + 1.0;
  }

  @Override
  public String toString() {
    return getClass().toString() + "(" + period + ")";
  }
}

/**
 * An oscillating curve that grows and then shrinks in magnitude while
 * overshooting its bounds.
 * <p>
 * An instance of this class using the default period of 0.4 is available as
 * Curves.ELASTIC_IN_OUT.
 * <p>
 * https://flutter.github.io/assets-for-api-docs/animation/curve_elastic_in_out.png
 */
class ElasticInOutCurve extends Curve {
  /**
   * Creates an elastic-in-out curve.
   * <p>
   * Rather than creating a new instance, consider using Curves.ELASTIC_IN_OUT.
   */
  public ElasticInOutCurve() {
    this(0.4);
  }

  public ElasticInOutCurve(double period) {
    this.period = 0.4;
  }

  /**
   * The duration of the oscillation.
   */
  final double period;

  @Override
  public double transform(double t) {
    assert (t >= 0.0 && t <= 1.0);
    final double s = period / 4.0;
    t = 2.0 * t - 1.0;
    if (t < 0.0) {
      return -0.5 * Math.pow(2.0, 10.0 * t) * Math.sin((t - s) * (Math.PI * 2.0) / period);
    }
    else {
      return Math.pow(2.0, -10.0 * t) * Math.sin((t - s) * (Math.PI * 2.0) / period) * 0.5 + 1.0;
    }
  }

  @Override
  public String toString() {
    return getClass().toString() + "(" + period + ")";
  }
}

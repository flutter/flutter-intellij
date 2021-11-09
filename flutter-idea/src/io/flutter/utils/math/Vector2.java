/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;


import java.util.Arrays;

/**
 * This class is ported from the Vector2 class in the Dart vector_math
 * package. The code is ported as is without concern for making the code
 * consistent with Java api conventions to keep the code consistent with
 * the Dart code to simplify using Transform Matrixes returned by Flutter.
 */
public class Vector2 implements Vector {
  final double[] _v2storage;

  /**
   * Construct a new vector with the specified values.
   */
  Vector2(double x, double y) {
    _v2storage = new double[]{x, y};
  }

  /**
   * Zero vector.
   */
  public Vector2() {
    _v2storage = new double[2];
  }

  /**
   * Set the values of [result] to the minimum of [a] and [b] for each line.
   */
  static void min(Vector2 a, Vector2 b, Vector2 result) {
    result.setX(Math.min(a.getX(), b.getX()));
    result.setY(Math.min(a.getY(), b.getY()));
  }

  /**
   * Set the values of [result] to the maximum of [a] and [b] for each line.
   */
  static void max(Vector2 a, Vector2 b, Vector2 result) {
    result
      .setX(Math.max(a.getX(), b.getX()));
    result
      .setY(Math.max(a.getY(), b.getY()));
  }

  /**
   * Interpolate between [min] and [max] with the amount of [a] using a linear
   * interpolation and store the values in [result].
   */
  static void mix(Vector2 min, Vector2 max, double a, Vector2 result) {
    result.setX(min.getX() + a * (max.getX() - min.getX()));
    result.setY(min.getY() + a * (max.getY()) - min.getY());
  }

  /**
   * Splat [value] into all lanes of the vector.
   */
  static Vector2 all(double value) {
    final Vector2 ret = new Vector2();
    ret.splat(value);
    return ret;
  }

  /**
   * Copy of [other].
   */
  static Vector2 copy(Vector2 other) {
    final Vector2 ret = new Vector2();
    ret.setFrom(other);
    return ret;
  }

  /**
   * The components of the vector.
   */
  @Override()
  public double[] getStorage() {
    return _v2storage;
  }

  /**
   * Set the values of the vector.
   */
  public void setValues(double x_, double y_) {
    _v2storage[0] = x_;
    _v2storage[1] = y_;
  }

  /**
   * Zero the vector.
   */
  public void setZero() {
    _v2storage[0] = 0.0;
    _v2storage[1] = 0.0;
  }

  /**
   * Set the values by copying them from [other].
   */
  public void setFrom(Vector2 other) {
    final double[] otherStorage = other._v2storage;
    _v2storage[1] = otherStorage[1];
    _v2storage[0] = otherStorage[0];
  }

  /**
   * Splat [arg] into all lanes of the vector.
   */
  public void splat(double arg) {
    _v2storage[0] = arg;
    _v2storage[1] = arg;
  }

  /**
   * Returns a printable string
   */
  @Override()
  public String toString() {
    return "[" + _v2storage[0] + "," + _v2storage[1] + "]";
  }

  /**
   * Check if two vectors are the same.
   */
  @Override()
  public boolean equals(Object other) {
    if (!(other instanceof Vector2)) return false;
    Vector2 otherV = (Vector2)other;
    return (_v2storage[0] == otherV._v2storage[0]) &&
           (_v2storage[1] == otherV._v2storage[1]);
  }

  @Override()
  public int hashCode() {
    return Arrays.hashCode(_v2storage);
  }

  /**
   * Negate.
   */
  Vector2 operatorNegate() {
    final Vector2 ret = clone();
    ret.negate();
    return ret;
  }

  /**
   * Subtract two vectors.
   */
  public Vector2 operatorSub(Vector2 other) {
    final Vector2 ret = clone();
    ret.sub(other);
    return ret;
  }

  /**
   * Add two vectors.
   */
  public Vector2 operatorAdd(Vector2 other) {
    final Vector2 ret = clone();
    ret.add(other);
    return ret;
  }

  /**
   * Scale.
   */
  public Vector2 operatorDiv(double scale) {
    final Vector2 ret = clone();
    ret.scale(1.0 / scale);
    return ret;
  }

  /**
   * Scale.
   */
  public Vector2 operatorScale(double scale) {
    final Vector2 ret = clone();
    ret.scale(scale);
    return ret;
  }

  /**
   * Length.
   */
  public double getLength() {
    return Math.sqrt(getLength2());
  }

  /**
   * Set the length of the vector. A negative [value] will change the vectors
   * orientation and a [value] of zero will set the vector to zero.
   */
  public void setLength(double value) {
    if (value == 0.0) {
      setZero();
    }
    else {
      double l = getLength();
      if (l == 0.0) {
        return;
      }
      l = value / l;
      _v2storage[0] *= l;
      _v2storage[1] *= l;
    }
  }

  /**
   * Length squared.
   */
  public double getLength2() {
    double sum;
    sum = (_v2storage[0] * _v2storage[0]);
    sum += (_v2storage[1] * _v2storage[1]);
    return sum;
  }

  /**
   * Normalize [this].
   */
  public double normalize() {
    final double l = getLength();
    if (l == 0.0) {
      return 0.0;
    }
    final double d = 1.0 / l;
    _v2storage[0] *= d;
    _v2storage[1] *= d;
    return l;
  }


  /**
   * Normalized copy of [this].
   */
  public Vector2 normalized() {
    final Vector2 ret = clone();
    ret.normalize();
    return ret;
  }

  /**
   * Normalize vector into [out].
   */
  public Vector2 normalizeInto(Vector2 out) {
    out.setFrom(this);
    out.normalize();
    return out;
  }

  /**
   * Distance from [this] to [arg]
   */
  public double distanceTo(Vector2 arg) {
    return Math.sqrt(distanceToSquared(arg));
  }

  /**
   * Squared distance from [this] to [arg]
   */
  public double distanceToSquared(Vector2 arg) {
    final double dx = getX() - arg.getX();
    final double dy = getY() - arg.getY();

    return dx * dx + dy * dy;
  }

  /**
   * Returns the angle between [this] vector and [other] in radians.
   */
  public double angleTo(Vector2 other) {
    final double[] otherStorage = other._v2storage;
    if (_v2storage[0] == otherStorage[0] && _v2storage[1] == otherStorage[1]) {
      return 0.0;
    }

    final double d = dot(other) / (getLength() * other.getLength());

    return Math.acos(VectorUtil.clamp(d, -1.0, 1.0));
  }

  /**
   * Returns the signed angle between [this] and [other] in radians.
   */
  public double angleToSigned(Vector2 other) {
    final double[] otherStorage = other._v2storage;
    if (_v2storage[0] == otherStorage[0] && _v2storage[1] == otherStorage[1]) {
      return 0.0;
    }

    final double s = cross(other);
    final double c = dot(other);

    return Math.atan2(s, c);
  }

  /**
   * Inner product.
   */
  public double dot(Vector2 other) {
    final double[] otherStorage = other._v2storage;
    double sum;
    sum = _v2storage[0] * otherStorage[0];
    sum += _v2storage[1] * otherStorage[1];
    return sum;
  }

  /**
   * Cross product.
   */
  public double cross(Vector2 other) {
    final double[] otherStorage = other._v2storage;
    return _v2storage[0] * otherStorage[1] - _v2storage[1] * otherStorage[0];
  }

  /**
   * Rotate [this] by 90 degrees then scale it. Store result in [out]. Return [out].
   */
  public Vector2 scaleOrthogonalInto(double scale, Vector2 out) {
    out.setValues(-scale * _v2storage[1], scale * _v2storage[0]);
    return out;
  }

  /**
   * Reflect [this].
   */
  public void reflect(Vector2 normal) {
    sub(normal.scaled(2.0 * normal.dot(this)));
  }

  /**
   * Reflected copy of [this].
   */
  public Vector2 reflected(Vector2 normal) {
    final Vector2 ret = clone();
    ret.reflect(normalized());
    return ret;
  }

  /**
   * Relative error between [this] and [correct]
   */
  public double relativeError(Vector2 correct) {
    final double correct_norm = correct.getLength();
    final double diff_norm = (this.operatorSub(correct)).getLength();
    return diff_norm / correct_norm;
  }

  /**
   * Absolute error between [this] and [correct]
   */
  public double absoluteError(Vector2 correct) {
    return operatorSub(correct).getLength();
  }

  /**
   * True if any component is infinite.
   */
  public boolean isInfinite() {
    boolean is_infinite = Double.isInfinite(_v2storage[0]);
    is_infinite = is_infinite || Double.isInfinite(_v2storage[1]);
    return is_infinite;
  }

  /**
   * True if any component is NaN.
   */
  public boolean isNaN() {
    boolean is_nan = Double.isNaN(_v2storage[0]);
    is_nan = is_nan || Double.isNaN(_v2storage[1]);
    return is_nan;
  }

  /**
   * Add [arg] to [this].
   */
  public void add(Vector2 arg) {
    final double[] argStorage = arg._v2storage;
    _v2storage[0] = _v2storage[0] + argStorage[0];
    _v2storage[1] = _v2storage[1] + argStorage[1];
  }

  /**
   * Add [arg] scaled by [factor] to [this].
   */
  public void addScaled(Vector2 arg, double factor) {
    final double[] argStorage = arg._v2storage;
    _v2storage[0] = _v2storage[0] + argStorage[0] * factor;
    _v2storage[1] = _v2storage[1] + argStorage[1] * factor;
  }

  /**
   * Subtract [arg] from [this].
   */
  public void sub(Vector2 arg) {
    final double[] argStorage = arg._v2storage;
    _v2storage[0] = _v2storage[0] - argStorage[0];
    _v2storage[1] = _v2storage[1] - argStorage[1];
  }

  /**
   * Multiply entries in [this] with entries in [arg].
   */
  public void multiply(Vector2 arg) {
    final double[] argStorage = arg._v2storage;
    _v2storage[0] = _v2storage[0] * argStorage[0];
    _v2storage[1] = _v2storage[1] * argStorage[1];
  }

  /**
   * Divide entries in [this] with entries in [arg].
   */
  public void divide(Vector2 arg) {
    final double[] argStorage = arg._v2storage;
    _v2storage[0] = _v2storage[0] / argStorage[0];
    _v2storage[1] = _v2storage[1] / argStorage[1];
  }

  /**
   * Scale [this] by [arg].
   */
  public void scale(double arg) {
    _v2storage[1] = _v2storage[1] * arg;
    _v2storage[0] = _v2storage[0] * arg;
  }

  /**
   * Return a copy of [this] scaled by [arg].
   */
  public Vector2 scaled(double arg) {
    final Vector2 ret = clone();
    ret.scale(arg);
    return ret;
  }

  /**
   * Negate.
   */
  public void negate() {
    _v2storage[1] = -_v2storage[1];
    _v2storage[0] = -_v2storage[0];
  }

  /**
   * Absolute value.
   */
  public void absolute() {
    _v2storage[1] = Math.abs(_v2storage[1]);
    _v2storage[0] = Math.abs(_v2storage[0]);
  }

  /**
   * Clamp each entry n in [this] in the range [min[n]]-[max[n]].
   */
  public void clamp(Vector2 min, Vector2 max) {
    final double[] minStorage = min.getStorage();
    final double[] maxStorage = max.getStorage();
    _v2storage[0] =
      VectorUtil.clamp(_v2storage[0], minStorage[0], maxStorage[0]);
    _v2storage[1] =
      VectorUtil.clamp(_v2storage[1], minStorage[1], maxStorage[1]);
  }

  /**
   * Clamp entries [this] in the range [min]-[max].
   */
  public void clampScalar(double min, double max) {
    _v2storage[0] = VectorUtil.clamp(_v2storage[0], min, max);
    _v2storage[1] = VectorUtil.clamp(_v2storage[1], min, max);
  }

  /**
   * Floor entries in [this].
   */
  public void floor() {
    _v2storage[0] = Math.floor(_v2storage[0]);
    _v2storage[1] = Math.floor(_v2storage[1]);
  }

  /**
   * Ceil entries in [this].
   */
  public void ceil() {
    _v2storage[0] = Math.ceil(_v2storage[0]);
    _v2storage[1] = Math.ceil(_v2storage[1]);
  }

  /**
   * Round entries in [this].
   */
  public void round() {
    _v2storage[0] = Math.round(_v2storage[0]);
    _v2storage[1] = Math.round(_v2storage[1]);
  }

  /**
   * Round entries in [this] towards zero.
   */
  public void roundToZero() {
    _v2storage[0] = _v2storage[0] < 0.0
                    ? Math.ceil(_v2storage[0])
                    : Math.floor(_v2storage[0]);
    _v2storage[1] = _v2storage[1] < 0.0
                    ? Math.ceil(_v2storage[1])
                    : Math.floor(_v2storage[1]);
  }

  /**
   * Clone of [this].
   */
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Vector2 clone() {
    final Vector2 ret = new Vector2();
    ret.setFrom(this);
    return ret;
  }

  /**
   * Copy [this] into [arg]. Returns [arg].
   */
  public Vector2 copyInto(Vector2 arg) {
    final double[] argStorage = arg._v2storage;
    argStorage[1] = _v2storage[1];
    argStorage[0] = _v2storage[0];
    return arg;
  }

  public double getX() {
    return _v2storage[0];
  }

  public void setX(double arg) {
    _v2storage[0] = arg;
  }

  public double getY() {
    return _v2storage[1];
  }

  public void setY(double arg) {
    _v2storage[1] = arg;
  }
}
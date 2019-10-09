/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

import java.util.Arrays;

/**
 * 4D column vector.
 * <p>
 * This class is ported from the Vector4 class in the Dart vector_math
 * package. The code is ported as is without concern for making the code
 * consistent with Java api conventions to keep the code consistent with
 * the Dart code to simplify using Transform Matrixes returned by Flutter.
 */
@SuppressWarnings("Duplicates")
class Vector4 implements Vector {
  final double[] _v4storage;

  /**
   * Construct a new vector with the specified values.
   */
  Vector4(double x, double y, double z, double w) {
    _v4storage = new double[]{x, y, z, w};
  }

  /**
   * Zero vector.
   */
  Vector4() {
    _v4storage = new double[4];
  }

  /**
   * Constructs Vector4 with given double[] as [.getStorage()].
   */
  Vector4(double[] values) {
    _v4storage = values;
  }

  /**
   * Set the values of [result] to the minimum of [a] and [b] for each line.
   */
  public static void min(Vector4 a, Vector4 b, Vector4 result) {
    result.setX(Math.min(a.getX(), b.getX()));
    result.setY(Math.min(a.getY(), b.getY()));
    result.setZ(Math.min(a.getZ(), b.getZ()));
    result.setW(Math.min(a.getW(), b.getW()));
  }

  /**
   * Set the values of [result] to the maximum of [a] and [b] for each line.
   */
  public static void max(Vector4 a, Vector4 b, Vector4 result) {
    result.setX(Math.max(a.getX(), b.getX()));
    result.setY(Math.max(a.getY(), b.getY()));
    result.setZ(Math.max(a.getZ(), b.getZ()));
    result.setW(Math.max(a.getW(), b.getW()));
  }

  /*
   * Interpolate between [min] and [max] with the amount of [a] using a linear
   * interpolation and store the values in [result].
   */
  public static void mix(Vector4 min, Vector4 max, double a, Vector4 result) {
    result.setX(min.getX() + a * (max.getX() - min.getX()));
    result.setY(min.getY() + a * (max.getY() - min.getY()));
    result.setZ(min.getZ() + a * (max.getZ() - min.getZ()));
    result.setW(min.getW() + a * (max.getW() - min.getW()));
  }

  /**
   * Constructs the identity vector.
   */
  public static Vector4 identity() {
    final Vector4 ret = new Vector4();
    ret.setIdentity();
    return ret;
  }

  /**
   * Splat [value] into all lanes of the vector.
   */
  public static Vector4 all(double value) {
    final Vector4 ret = new Vector4();
    ret.splat(value);
    return ret;
  }

  /**
   * Copy of [other].
   */
  public static Vector4 copy(Vector4 other) {
    final Vector4 ret = new Vector4();
    ret.setFrom(other);
    return ret;
  }

  /**
   * The components of the vector.
   */
  @Override()
  public double[] getStorage() {
    return _v4storage;
  }

  /**
   * Set the values of the vector.
   */
  public void setValues(double x_, double y_, double z_, double w_) {
    _v4storage[3] = w_;
    _v4storage[2] = z_;
    _v4storage[1] = y_;
    _v4storage[0] = x_;
  }

  /**
   * Zero the vector.
   */
  public void setZero() {
    _v4storage[0] = 0.0;
    _v4storage[1] = 0.0;
    _v4storage[2] = 0.0;
    _v4storage[3] = 0.0;
  }

  /**
   * Set to the identity vector.
   */
  public void setIdentity() {
    _v4storage[0] = 0.0;
    _v4storage[1] = 0.0;
    _v4storage[2] = 0.0;
    _v4storage[3] = 1.0;
  }

  /**
   * Set the values by copying them from [other].
   */
  public void setFrom(Vector4 other) {
    final double[] otherStorage = other._v4storage;
    _v4storage[3] = otherStorage[3];
    _v4storage[2] = otherStorage[2];
    _v4storage[1] = otherStorage[1];
    _v4storage[0] = otherStorage[0];
  }

  /**
   * Splat [arg] into all lanes of the vector.
   */
  public void splat(double arg) {
    _v4storage[3] = arg;
    _v4storage[2] = arg;
    _v4storage[1] = arg;
    _v4storage[0] = arg;
  }

  /**
   * Returns a printable string
   */
  @Override()
  public String toString() {
    return "" + _v4storage[0] + "," + _v4storage[1] + "," + _v4storage[2] + "," + _v4storage[3];
  }

  /**
   * Check if two vectors are the same.
   */
  @Override()
  public boolean equals(Object o) {
    if (!(o instanceof Vector4)) {
      return false;
    }
    final Vector4 other = (Vector4)o;
    return (_v4storage[0] == other._v4storage[0]) &&
           (_v4storage[1] == other._v4storage[1]) &&
           (_v4storage[2] == other._v4storage[2]) &&
           (_v4storage[3] == other._v4storage[3]);
  }

  @Override()
  public int hashCode() {
    return Arrays.hashCode(_v4storage);
  }

  /**
   * Negate.
   */
  public Vector4 operatorNegate() {
    final Vector4 ret = clone();
    ret.negate();
    return ret;
  }

  /**
   * Subtract two vectors.
   */
  public Vector4 operatorSub(Vector4 other) {
    final Vector4 ret = clone();
    ret.sub(other);
    return ret;
  }

  /**
   * Add two vectors.
   */
  public Vector4 operatorAdd(Vector4 other) {
    final Vector4 ret = clone();
    ret.add(other);
    return ret;
  }

  /**
   * Scale.
   */
  public Vector4 operatorDiv(double scale) {
    final Vector4 ret = clone();
    ret.scale(1.0 / scale);
    return ret;
  }

  /**
   * Scale.
   */
  public Vector4 operatorScale(double scale) {
    final Vector4 ret = clone();
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
      _v4storage[0] *= l;
      _v4storage[1] *= l;
      _v4storage[2] *= l;
      _v4storage[3] *= l;
    }
  }

  /**
   * Length squared.
   */
  public double getLength2() {
    double sum;
    sum = (_v4storage[0] * _v4storage[0]);
    sum += (_v4storage[1] * _v4storage[1]);
    sum += (_v4storage[2] * _v4storage[2]);
    sum += (_v4storage[3] * _v4storage[3]);
    return sum;
  }

  /**
   * Normalizes [this].
   */
  public double normalize() {
    final double l = getLength();
    if (l == 0.0) {
      return 0.0;
    }
    final double d = 1.0 / l;
    _v4storage[0] *= d;
    _v4storage[1] *= d;
    _v4storage[2] *= d;
    _v4storage[3] *= d;
    return l;
  }

  /**
   * Normalizes copy of [this].
   */
  public Vector4 normalized() {
    final Vector4 ret = clone();
    ret.normalize();
    return ret;
  }

  /**
   * Normalize vector into [out].
   */
  public Vector4 normalizeInto(Vector4 out) {
    out.setFrom(this);
    out.normalize();
    return out;
  }

  /**
   * Distance from [this] to [arg]
   */
  public double distanceTo(Vector4 arg) {
    return Math.sqrt(distanceToSquared(arg));
  }

  /**
   * Squared distance from [this] to [arg]
   */
  public double distanceToSquared(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    final double dx = _v4storage[0] - argStorage[0];
    final double dy = _v4storage[1] - argStorage[1];
    final double dz = _v4storage[2] - argStorage[2];
    final double dw = _v4storage[3] - argStorage[3];

    return dx * dx + dy * dy + dz * dz + dw * dw;
  }

  /**
   * Inner product.
   */
  public double dot(Vector4 other) {
    final double[] otherStorage = other._v4storage;
    double sum;
    sum = _v4storage[0] * otherStorage[0];
    sum += _v4storage[1] * otherStorage[1];
    sum += _v4storage[2] * otherStorage[2];
    sum += _v4storage[3] * otherStorage[3];
    return sum;
  }

  /**
   * Multiplies [this] by [arg].
   */
  public void applyMatrix4(Matrix4 arg) {
    final double v1 = _v4storage[0];
    final double v2 = _v4storage[1];
    final double v3 = _v4storage[2];
    final double v4 = _v4storage[3];
    final double[] argStorage = arg.getStorage();
    _v4storage[0] = argStorage[0] * v1 +
                    argStorage[4] * v2 +
                    argStorage[8] * v3 +
                    argStorage[12] * v4;
    _v4storage[1] = argStorage[1] * v1 +
                    argStorage[5] * v2 +
                    argStorage[9] * v3 +
                    argStorage[13] * v4;
    _v4storage[2] = argStorage[2] * v1 +
                    argStorage[6] * v2 +
                    argStorage[10] * v3 +
                    argStorage[14] * v4;
    _v4storage[3] = argStorage[3] * v1 +
                    argStorage[7] * v2 +
                    argStorage[11] * v3 +
                    argStorage[15] * v4;
  }

  /**
   * Relative error between [this] and [correct]
   */
  double relativeError(Vector4 correct) {
    final double correct_norm = correct.getLength();
    final double diff_norm = (this.operatorSub(correct)).getLength();
    return diff_norm / correct_norm;
  }

  /**
   * Absolute error between [this] and [correct]
   */
  double absoluteError(Vector4 correct) {
    return (this.operatorSub(correct)).getLength();
  }

  /**
   * True if any component is infinite.
   */
  public boolean isInfinite() {
    boolean is_infinite;
    is_infinite = Double.isInfinite(_v4storage[0]);
    is_infinite = is_infinite || Double.isInfinite(_v4storage[1]);
    is_infinite = is_infinite || Double.isInfinite(_v4storage[2]);
    is_infinite = is_infinite || Double.isInfinite(_v4storage[3]);
    return is_infinite;
  }

  /**
   * True if any component is NaN.
   */
  public boolean isNaN() {
    boolean is_nan;
    is_nan = Double.isNaN(_v4storage[0]);
    is_nan = is_nan || Double.isNaN(_v4storage[1]);
    is_nan = is_nan || Double.isNaN(_v4storage[2]);
    is_nan = is_nan || Double.isNaN(_v4storage[3]);
    return is_nan;
  }

  public void add(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    _v4storage[0] = _v4storage[0] + argStorage[0];
    _v4storage[1] = _v4storage[1] + argStorage[1];
    _v4storage[2] = _v4storage[2] + argStorage[2];
    _v4storage[3] = _v4storage[3] + argStorage[3];
  }

  /**
   * Add [arg] scaled by [factor] to [this].
   */
  public void addScaled(Vector4 arg, double factor) {
    final double[] argStorage = arg._v4storage;
    _v4storage[0] = _v4storage[0] + argStorage[0] * factor;
    _v4storage[1] = _v4storage[1] + argStorage[1] * factor;
    _v4storage[2] = _v4storage[2] + argStorage[2] * factor;
    _v4storage[3] = _v4storage[3] + argStorage[3] * factor;
  }

  /**
   * Subtract [arg] from [this].
   */
  public void sub(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    _v4storage[0] = _v4storage[0] - argStorage[0];
    _v4storage[1] = _v4storage[1] - argStorage[1];
    _v4storage[2] = _v4storage[2] - argStorage[2];
    _v4storage[3] = _v4storage[3] - argStorage[3];
  }

  /**
   * Multiply [this] by [arg].
   */
  public void multiply(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    _v4storage[0] = _v4storage[0] * argStorage[0];
    _v4storage[1] = _v4storage[1] * argStorage[1];
    _v4storage[2] = _v4storage[2] * argStorage[2];
    _v4storage[3] = _v4storage[3] * argStorage[3];
  }

  /**
   * Divide [this] by [arg].
   */
  public void div(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    _v4storage[0] = _v4storage[0] / argStorage[0];
    _v4storage[1] = _v4storage[1] / argStorage[1];
    _v4storage[2] = _v4storage[2] / argStorage[2];
    _v4storage[3] = _v4storage[3] / argStorage[3];
  }

  /**
   * Scale [this] by [arg].
   */
  public void scale(double arg) {
    _v4storage[0] = _v4storage[0] * arg;
    _v4storage[1] = _v4storage[1] * arg;
    _v4storage[2] = _v4storage[2] * arg;
    _v4storage[3] = _v4storage[3] * arg;
  }

  /**
   * Create a copy of [this] scaled by [arg].
   */
  public Vector4 scaled(double arg) {
    final Vector4 ret = clone();
    ret.scale(arg);
    return ret;
  }

  /**
   * Negate [this].
   */
  public void negate() {
    _v4storage[0] = -_v4storage[0];
    _v4storage[1] = -_v4storage[1];
    _v4storage[2] = -_v4storage[2];
    _v4storage[3] = -_v4storage[3];
  }

  /**
   * Set [this] to the absolute.
   */
  public void absolute() {
    _v4storage[3] = Math.abs(_v4storage[3]);
    _v4storage[2] = Math.abs(_v4storage[2]);
    _v4storage[1] = Math.abs(_v4storage[1]);
    _v4storage[0] = Math.abs(_v4storage[0]);
  }

  /**
   * Clamp each entry n in [this] in the range [min[n]]-[max[n]].
   */
  public void clamp(Vector4 min, Vector4 max) {
    final double[] minStorage = min.getStorage();
    final double[] maxStorage = max.getStorage();
    _v4storage[0] = VectorUtil.clamp(
      _v4storage[0], minStorage[0], maxStorage[0]);
    _v4storage[1] = VectorUtil.clamp(
      _v4storage[1], minStorage[1], maxStorage[1]);
    _v4storage[2] = VectorUtil.clamp(
      _v4storage[2], minStorage[2], maxStorage[2]);
    _v4storage[3] = VectorUtil.clamp(
      _v4storage[3], minStorage[3], maxStorage[3]);
  }

  /**
   * Clamp entries in [this] in the range [min]-[max].
   */
  public void clampScalar(double min, double max) {
    _v4storage[0] = VectorUtil.clamp(_v4storage[0], min, max);
    _v4storage[1] = VectorUtil.clamp(_v4storage[1], min, max);
    _v4storage[2] = VectorUtil.clamp(_v4storage[2], min, max);
    _v4storage[3] = VectorUtil.clamp(_v4storage[3], min, max);
  }

  /**
   * Floor entries in [this].
   */
  public void floor() {
    _v4storage[0] = Math.floor(_v4storage[0]);
    _v4storage[1] = Math.floor(_v4storage[1]);
    _v4storage[2] = Math.floor(_v4storage[2]);
    _v4storage[3] = Math.floor(_v4storage[3]);
  }

  /**
   * Ceil entries in [this].
   */
  public void ceil() {
    _v4storage[0] = Math.ceil(_v4storage[0]);
    _v4storage[1] = Math.ceil(_v4storage[1]);
    _v4storage[2] = Math.ceil(_v4storage[2]);
    _v4storage[3] = Math.ceil(_v4storage[3]);
  }

  /**
   * Round entries in [this].
   */
  public void round() {
    _v4storage[0] = Math.round(_v4storage[0]);
    _v4storage[1] = Math.round(_v4storage[1]);
    _v4storage[2] = Math.round(_v4storage[2]);
    _v4storage[3] = Math.round(_v4storage[3]);
  }

  /**
   * Round entries in [this] towards zero.
   */
  public void roundToZero() {
    _v4storage[0] = _v4storage[0] < 0.0
                    ? Math.ceil(_v4storage[0])
                    : Math.floor(_v4storage[0]);
    _v4storage[1] = _v4storage[1] < 0.0
                    ? Math.ceil(_v4storage[1])
                    : Math.floor(_v4storage[1]);
    _v4storage[2] = _v4storage[2] < 0.0
                    ? Math.ceil(_v4storage[2])
                    : Math.floor(_v4storage[2]);
    _v4storage[3] = _v4storage[3] < 0.0
                    ? Math.ceil(_v4storage[3])
                    : Math.floor(_v4storage[3]);
  }

  /**
   * Create a copy of [this].
   */
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Vector4 clone() {
    return Vector4.copy(this);
  }

  /**
   * Copy [this]
   */
  public Vector4 copyInto(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    argStorage[0] = _v4storage[0];
    argStorage[1] = _v4storage[1];
    argStorage[2] = _v4storage[2];
    argStorage[3] = _v4storage[3];
    return arg;
  }

  public double getX() {
    return _v4storage[0];
  }

  public void setX(double arg) {
    _v4storage[0] = arg;
  }

  public double getY() {
    return _v4storage[1];
  }

  public void setY(double arg) {
    _v4storage[1] = arg;
  }

  public double getZ() {
    return _v4storage[2];
  }

  public void setZ(double arg) {
    _v4storage[2] = arg;
  }

  public double getW() {
    return _v4storage[3];
  }

  public void setW(double arg) {
    _v4storage[3] = arg;
  }
}
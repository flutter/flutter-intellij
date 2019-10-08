/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

import java.util.Arrays;

@SuppressWarnings({"PointlessArithmeticExpression", "UnusedReturnValue", "DuplicatedCode", "ConstantConditions"})
/**
 * This class is ported from the Vector3 class in the Dart vector_math
 * package. The code is ported as is without concern for making the code
 * consistent with Java api conventions to keep the code consistent with
 * the Dart code to simplify using Transform Matrixes returned by Flutter.
 */
public class Vector3 implements Vector {
  final double[] _v3storage;

  /**
   * Construct a new vector with the specified values.
   */
  public Vector3(double x, double y, double z) {
    _v3storage = new double[]{x, y, z};
  }

  /**
   * Zero vector.
   */
  Vector3() {
    _v3storage = new double[3];
  }


  /**
   * Constructs Vector3 with given double[] as [storage].
   */
  public Vector3(double[] v3storage) {
    this._v3storage = v3storage;
  }

  /**
   * Set the values of [result] to the minimum of [a] and [b] for each line.
   */
  public static void min(Vector3 a, Vector3 b, Vector3 result) {
    result.setX(Math.min(a.getX(), b.getX()));
    result.setY(Math.min(a.getY(), b.getY()));
    result.setZ(Math.min(a.getZ(), b.getZ()));
  }

  /**
   * Set the values of [result] to the maximum of [a] and [b] for each line.
   */
  public static void max(Vector3 a, Vector3 b, Vector3 result) {
    result.setX(Math.max(a.getX(), b.getX()));
    result.setY(Math.max(a.getY(), b.getY()));
    result.setZ(Math.max(a.getZ(), b.getZ()));
  }

  /*
   * Interpolate between [min] and [max] with the amount of [a] using a linear
   * interpolation and store the values in [result].
   */
  public static void mix(Vector3 min, Vector3 max, double a, Vector3 result) {
    result.setX(min.getX() + a * (max.getX() - min.getX()));
    result.setY(min.getY() + a * (max.getY() - min.getY()));
    result.setZ(min.getZ() + a * (max.getZ() - min.getZ()));
  }

  /**
   * Initialized with values from [array] starting at [offset].
   */
  public static Vector3 array(double[] array) {
    return array(array, 0);
  }

  public static Vector3 array(double[] array, int offset) {
    final Vector3 ret = new Vector3();
    ret.copyFromArray(array, offset);
    return ret;
  }

  public static Vector3 getZero() {
    return new Vector3();
  }

  /**
   * Splat [value] into all lanes of the vector.
   */
  static Vector3 all(double value) {
    final Vector3 ret = Vector3.getZero();
    ret.splat(value);
    return ret;
  }

  /**
   * Copy of [other].
   */
  static Vector3 copy(Vector3 other) {
    final Vector3 ret = Vector3.getZero();
    ret.setFrom(other);
    return ret;
  }

  public static Vector3 zero() {
    return new Vector3();
  }

  /**
   * The components of the vector.
   */
  @Override()
  public double[] getStorage() {
    return _v3storage;
  }

  /**
   * Set the values of the vector.
   */
  public void setValues(double x_, double y_, double z_) {
    _v3storage[0] = x_;
    _v3storage[1] = y_;
    _v3storage[2] = z_;
  }

  /**
   * Zero vector.
   */
  public void setZero() {
    _v3storage[2] = 0.0;
    _v3storage[1] = 0.0;
    _v3storage[0] = 0.0;
  }

  /**
   * Set the values by copying them from [other].
   */
  public void setFrom(Vector3 other) {
    final double[] otherStorage = other._v3storage;
    _v3storage[0] = otherStorage[0];
    _v3storage[1] = otherStorage[1];
    _v3storage[2] = otherStorage[2];
  }

  /**
   * Splat [arg] into all lanes of the vector.
   */
  public void splat(double arg) {
    _v3storage[2] = arg;
    _v3storage[1] = arg;
    _v3storage[0] = arg;
  }

  /**
   * Returns a printable string
   */
  @Override()
  public String toString() {
    return "[" + _v3storage[0] + "," + _v3storage[1] + "," + _v3storage[2] + "]";
  }

  /**
   * Check if two vectors are the same.
   */
  @Override()
  public boolean equals(Object o) {
    if (!(o instanceof Vector3)) {
      return false;
    }
    final Vector3 other = (Vector3)o;
    return (_v3storage[0] == other._v3storage[0]) &&
           (_v3storage[1] == other._v3storage[1]) &&
           (_v3storage[2] == other._v3storage[2]);
  }

  @Override()
  public int hashCode() {
    return Arrays.hashCode(_v3storage);
  }

  /**
   * Negate
   */
  public Vector3 operatorNegate() {
    final Vector3 ret = clone();
    ret.negate();
    return ret;
  }

  /**
   * Subtract two vectors.
   */
  public Vector3 operatorSub(Vector3 other) {
    final Vector3 ret = clone();
    ret.sub(other);
    return ret;
  }

  /**
   * Add two vectors.
   */
  public Vector3 operatorAdd(Vector3 other) {
    final Vector3 ret = clone();
    ret.add(other);
    return ret;
  }

  /**
   * Scale.
   */
  public Vector3 operatorDiv(double scale) {
    final Vector3 ret = clone();
    ret.scaled(1.0 / scale);
    return ret;
  }

  /**
   * Scale by [scale].
   */
  public Vector3 operatorScaled(double scale) {
    final Vector3 ret = clone();
    ret.scaled(scale);
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
      _v3storage[0] *= l;
      _v3storage[1] *= l;
      _v3storage[2] *= l;
    }
  }

  /**
   * Length squared.
   */
  public double getLength2() {
    double sum;
    sum = (_v3storage[0] * _v3storage[0]);
    sum += (_v3storage[1] * _v3storage[1]);
    sum += (_v3storage[2] * _v3storage[2]);
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
    _v3storage[0] *= d;
    _v3storage[1] *= d;
    _v3storage[2] *= d;
    return l;
  }

  /**
   * Normalizes copy of [this].
   */
  public Vector3 normalized() {
    final Vector3 ret = Vector3.copy(this);
    ret.normalize();
    return ret;
  }

  /**
   * Normalize vector into [out].
   */
  public Vector3 normalizeInto(Vector3 out) {
    out.setFrom(this);
    out.normalize();
    return out;
  }

  /**
   * Distance from [this] to [arg]
   */
  public double distanceTo(Vector3 arg) {
    return Math.sqrt(distanceToSquared(arg));
  }

  /**
   * Squared distance from [this] to [arg]
   */
  public double distanceToSquared(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    final double dx = _v3storage[0] - argStorage[0];
    final double dy = _v3storage[1] - argStorage[1];
    final double dz = _v3storage[2] - argStorage[2];

    return dx * dx + dy * dy + dz * dz;
  }

  /**
   * Returns the angle between [this] vector and [other] in radians.
   */
  public double angleTo(Vector3 other) {
    final double[] otherStorage = other._v3storage;
    if (_v3storage[0] == otherStorage[0] &&
        _v3storage[1] == otherStorage[1] &&
        _v3storage[2] == otherStorage[2]) {
      return 0.0;
    }

    final double d = dot(other) / (getLength() * other.getLength());

    return Math.acos(VectorUtil.clamp(d, -1.0, 1.0));
  }

  /**
   * Returns the signed angle between [this] and [other] around [normal]
   * in radians.
   */
  public double angleToSigned(Vector3 other, Vector3 normal) {
    final double angle = angleTo(other);
    final Vector3 c = cross(other);
    final double d = c.dot(normal);

    return d < 0.0 ? -angle : angle;
  }

  /**
   * Inner product.
   */
  public double dot(Vector3 other) {
    final double[] otherStorage = other._v3storage;
    double sum;
    sum = _v3storage[0] * otherStorage[0];
    sum += _v3storage[1] * otherStorage[1];
    sum += _v3storage[2] * otherStorage[2];
    return sum;
  }

  /**
   * Cross product.
   */
  public Vector3 cross(Vector3 other) {
    final double _x = _v3storage[0];
    final double _y = _v3storage[1];
    final double _z = _v3storage[2];
    final double[] otherStorage = other._v3storage;
    final double ox = otherStorage[0];
    final double oy = otherStorage[1];
    final double oz = otherStorage[2];
    return new Vector3(_y * oz - _z * oy, _z * ox - _x * oz, _x * oy - _y * ox);
  }

  /**
   * Cross product. Stores result in [out].
   */
  public Vector3 crossInto(Vector3 other, Vector3 out) {
    final double x = _v3storage[0];
    final double y = _v3storage[1];
    final double z = _v3storage[2];
    final double[] otherStorage = other._v3storage;
    final double ox = otherStorage[0];
    final double oy = otherStorage[1];
    final double oz = otherStorage[2];
    final double[] outStorage = out._v3storage;
    outStorage[0] = y * oz - z * oy;
    outStorage[1] = z * ox - x * oz;
    outStorage[2] = x * oy - y * ox;
    return out;
  }

  /**
   * Reflect [this].
   */
  public Vector3 reflect(Vector3 normal) {
    sub(normal.scaled(2.0 * normal.dot(this)));
    return this;
  }

  /**
   * Reflected copy of [this].
   */
  public Vector3 reflected(Vector3 normal) {
    final Vector3 ret = clone();
    ret.reflect(normal);
    return ret;
  }

  /**
   * Projects [this] using the projection matrix [arg]
   */
  public void applyProjection(Matrix4 arg) {
    final double[] argStorage = arg.getStorage();
    final double x = _v3storage[0];
    final double y = _v3storage[1];
    final double z = _v3storage[2];
    final double d = 1.0 /
                     (argStorage[3] * x +
                      argStorage[7] * y +
                      argStorage[11] * z +
                      argStorage[15]);
    _v3storage[0] = (argStorage[0] * x +
                     argStorage[4] * y +
                     argStorage[8] * z +
                     argStorage[12]) *
                    d;
    _v3storage[1] = (argStorage[1] * x +
                     argStorage[5] * y +
                     argStorage[9] * z +
                     argStorage[13]) *
                    d;
    _v3storage[2] = (argStorage[2] * x +
                     argStorage[6] * y +
                     argStorage[10] * z +
                     argStorage[14]) *
                    d;
  }

  /**
   * Applies a rotation specified by [axis] and [angle].
   */
  public void applyAxisAngle(Vector3 axis, double angle) {
    applyQuaternion(Quaternion.axisAngle(axis, angle));
  }

  /**
   * Applies a quaternion transform.
   */
  public void applyQuaternion(Quaternion arg) {
    final double[] argStorage = arg._qStorage;
    final double v0 = _v3storage[0];
    final double v1 = _v3storage[1];
    final double v2 = _v3storage[2];
    final double qx = argStorage[0];
    final double qy = argStorage[1];
    final double qz = argStorage[2];
    final double qw = argStorage[3];
    final double ix = qw * v0 + qy * v2 - qz * v1;
    final double iy = qw * v1 + qz * v0 - qx * v2;
    final double iz = qw * v2 + qx * v1 - qy * v0;
    final double iw = -qx * v0 - qy * v1 - qz * v2;
    _v3storage[0] = ix * qw + iw * -qx + iy * -qz - iz * -qy;
    _v3storage[1] = iy * qw + iw * -qy + iz * -qx - ix * -qz;
    _v3storage[2] = iz * qw + iw * -qz + ix * -qy - iy * -qx;
  }

  /**
   * Multiplies [this] by a 4x3 subset of [arg]. Expects [arg] to be an affine
   * transformation matrix.
   */
  public void applyMatrix4(Matrix4 arg) {
    final double[] argStorage = arg.getStorage();
    final double v0 = _v3storage[0];
    final double v1 = _v3storage[1];
    final double v2 = _v3storage[2];
    _v3storage[0] = argStorage[0] * v0 +
                    argStorage[4] * v1 +
                    argStorage[8] * v2 +
                    argStorage[12];
    _v3storage[1] = argStorage[1] * v0 +
                    argStorage[5] * v1 +
                    argStorage[9] * v2 +
                    argStorage[13];
    _v3storage[2] = argStorage[2] * v0 +
                    argStorage[6] * v1 +
                    argStorage[10] * v2 +
                    argStorage[14];
  }

  /**
   * Relative error between [this] and [correct]
   */
  public double relativeError(Vector3 correct) {
    final double correct_norm = correct.getLength();
    final double diff_norm = (this.operatorSub(correct)).getLength();
    return diff_norm / correct_norm;
  }

  /**
   * Absolute error between [this] and [correct]
   */
  public double absoluteError(Vector3 correct) {
    return (this.operatorSub(correct)).getLength();
  }

  /**
   * True if any component is infinite.
   */
  public boolean isInfinite() {
    boolean is_infinite = false;
    is_infinite = is_infinite || Double.isInfinite(_v3storage[0]);
    is_infinite = is_infinite || Double.isInfinite(_v3storage[1]);
    is_infinite = is_infinite || Double.isInfinite(_v3storage[2]);
    return is_infinite;
  }

  /**
   * True if any component is NaN.
   */
  public boolean isNaN() {
    boolean is_nan = false;
    is_nan = is_nan || Double.isNaN(_v3storage[0]);
    is_nan = is_nan || Double.isNaN(_v3storage[1]);
    is_nan = is_nan || Double.isNaN(_v3storage[2]);
    return is_nan;
  }

  /**
   * Add [arg] to [this].
   */
  public void add(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    _v3storage[0] = _v3storage[0] + argStorage[0];
    _v3storage[1] = _v3storage[1] + argStorage[1];
    _v3storage[2] = _v3storage[2] + argStorage[2];
  }

  /**
   * Add [arg] scaled by [factor] to [this].
   */
  public void addScaled(Vector3 arg, double factor) {
    final double[] argStorage = arg._v3storage;
    _v3storage[0] = _v3storage[0] + argStorage[0] * factor;
    _v3storage[1] = _v3storage[1] + argStorage[1] * factor;
    _v3storage[2] = _v3storage[2] + argStorage[2] * factor;
  }

  /**
   * Subtract [arg] from [this].
   */
  public Vector3 sub(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    _v3storage[0] = _v3storage[0] - argStorage[0];
    _v3storage[1] = _v3storage[1] - argStorage[1];
    _v3storage[2] = _v3storage[2] - argStorage[2];
    return this;
  }

  /**
   * Multiply entries in [this] with entries in [arg].
   */
  public Vector3 multiply(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    _v3storage[0] = _v3storage[0] * argStorage[0];
    _v3storage[1] = _v3storage[1] * argStorage[1];
    _v3storage[2] = _v3storage[2] * argStorage[2];
    return this;
  }

  /**
   * Divide entries in [this] with entries in [arg].
   */
  public Vector3 divide(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    _v3storage[0] = _v3storage[0] / argStorage[0];
    _v3storage[1] = _v3storage[1] / argStorage[1];
    _v3storage[2] = _v3storage[2] / argStorage[2];
    return this;
  }

  /**
   * Scale [this].
   */
  public void scale(double arg) {
    _v3storage[2] = _v3storage[2] * arg;
    _v3storage[1] = _v3storage[1] * arg;
    _v3storage[0] = _v3storage[0] * arg;
  }

  /**
   * Create a copy of [this] and scale it by [arg].
   */
  public Vector3 scaled(double arg) {
    final Vector3 ret = clone();
    ret.scale(arg);
    return ret;
  }

  /**
   * Negate [this].
   */
  public void negate() {
    _v3storage[2] = -_v3storage[2];
    _v3storage[1] = -_v3storage[1];
    _v3storage[0] = -_v3storage[0];
  }

  /**
   * Absolute value.
   */
  public void absolute() {
    _v3storage[0] = Math.abs(_v3storage[0]);
    _v3storage[1] = Math.abs(_v3storage[1]);
    _v3storage[2] = Math.abs(_v3storage[2]);
  }

  /**
   * Clamp each entry n in [this] in the range [min[n]]-[max[n]].
   */
  public void clamp(Vector3 min, Vector3 max) {
    final double[] minStorage = min.getStorage();
    final double[] maxStorage = max.getStorage();
    _v3storage[0] =
      VectorUtil.clamp(_v3storage[0], minStorage[0], maxStorage[0]);
    _v3storage[1] =
      VectorUtil.clamp(_v3storage[1], minStorage[1], maxStorage[1]);
    _v3storage[2] =
      VectorUtil.clamp(_v3storage[2], minStorage[2], maxStorage[2]);
  }

  /**
   * Clamp entries in [this] in the range [min]-[max].
   */
  public void clampScalar(double min, double max) {
    _v3storage[0] = VectorUtil.clamp(_v3storage[0], min, max);
    _v3storage[1] = VectorUtil.clamp(_v3storage[1], min, max);
    _v3storage[2] = VectorUtil.clamp(_v3storage[2], min, max);
  }

  /**
   * Floor entries in [this].
   */
  public void floor() {
    _v3storage[0] = Math.floor(_v3storage[0]);
    _v3storage[1] = Math.floor(_v3storage[1]);
    _v3storage[2] = Math.floor(_v3storage[2]);
  }

  /**
   * Ceil entries in [this].
   */
  public void ceil() {
    _v3storage[0] = Math.ceil(_v3storage[0]);
    _v3storage[1] = Math.ceil(_v3storage[1]);
    _v3storage[2] = Math.ceil(_v3storage[2]);
  }

  /**
   * Round entries in [this].
   */
  public void round() {
    _v3storage[0] = Math.round(_v3storage[0]);
    _v3storage[1] = Math.round(_v3storage[1]);
    _v3storage[2] = Math.round(_v3storage[2]);
  }

  /**
   * Round entries in [this] towards zero.
   */
  public void roundToZero() {
    _v3storage[0] = _v3storage[0] < 0.0
                    ? Math.ceil(_v3storage[0])
                    : Math.floor(_v3storage[0]);
    _v3storage[1] = _v3storage[1] < 0.0
                    ? Math.ceil(_v3storage[1])
                    : Math.floor(_v3storage[1]);
    _v3storage[2] = _v3storage[2] < 0.0
                    ? Math.ceil(_v3storage[2])
                    : Math.floor(_v3storage[2]);
  }

  /**
   * Clone of [this].
   */
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Vector3 clone() {
    final Vector3 ret = new Vector3();
    copyInto(ret);
    return ret;
  }

  /**
   * Copy [this] into [arg].
   */
  public Vector3 copyInto(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    argStorage[0] = _v3storage[0];
    argStorage[1] = _v3storage[1];
    argStorage[2] = _v3storage[2];
    return arg;
  }

  /**
   * Copies [this] into [array] starting at [offset].
   */
  public void copyIntoArray(double[] array) {
    copyIntoArray(array, 0);
  }

  public void copyIntoArray(double[] array, int offset) {
    array[offset + 2] = _v3storage[2];
    array[offset + 1] = _v3storage[1];
    array[offset + 0] = _v3storage[0];
  }

  /**
   * Copies elements from [array] into [this] starting at [offset].
   */
  public void copyFromArray(double[] array) {
    copyFromArray(array, 0);
  }

  public void copyFromArray(double[] array, int offset) {
    _v3storage[2] = array[offset + 2];
    _v3storage[1] = array[offset + 1];
    _v3storage[0] = array[offset + 0];
  }

  public double getX() {
    return _v3storage[0];
  }

  public void setX(double arg) {
    _v3storage[0] = arg;
  }

  public double getY() {
    return _v3storage[1];
  }

  public void setY(double arg) {
    _v3storage[1] = arg;
  }

  public double getZ() {
    return _v3storage[2];
  }

  public void setZ(double arg) {
    _v3storage[2] = arg;
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

/**
 * Defines a [Quaternion] (a four-dimensional vector) for efficient rotation
 * calculations.
 * <p>
 * This code is ported from the Quaternion class in the Dart vector_math
 * package. The Class is ported as is without concern for making the code
 * consistent with Java api conventions to keep the code consistent with
 * the Dart code to simplify using Transform Matrixes returned by Flutter.
 * <p>
 * Quaternion are better for interpolating between rotations and avoid the
 * [gimbal lock](http://de.wikipedia.org/wiki/Gimbal_Lock) problem compared to
 * euler rotations.
 */
@SuppressWarnings({"Duplicates", "UnnecessaryLocalVariable"})
class Quaternion {
  final double[] _qStorage;

  private Quaternion() {
    _qStorage = new double[4];
  }

  public Quaternion(Quaternion other) {
    _qStorage = other._qStorage.clone();
  }

  /**
   * Constructs a quaternion using the raw values [x], [y], [z], and [w].
   */
  public Quaternion(double x, double y, double z, double w) {
    _qStorage = new double[4];
    setValues(x, y, z, w);
  }

  /**
   * Constructs a quaternion with given double[] as [storage].
   */
  public Quaternion(double[] _qStorage) {
    this._qStorage = _qStorage;
  }

  /**
   * Constructs a quaternion from a rotation of [angle] around [axis].
   */
  public static Quaternion axisAngle(Vector3 axis, double angle) {
    final Quaternion ret = new Quaternion();
    ret.setAxisAngle(axis, angle);
    return ret;
  }

  /**
   * Constructs a quaternion to be the rotation that rotates vector [a] to [b].
   */
  public static Quaternion fromTwoVectors(Vector3 a, Vector3 b) {
    final Quaternion ret = new Quaternion();
    ret.setFromTwoVectors(a, b);
    return ret;
  }

  /**
   * Constructs a quaternion as a copy of [original].
   */
  public static Quaternion copy(Quaternion original) {
    final Quaternion ret = new Quaternion();
    ret.setFrom(original);
    return ret;
  }

  /**
   * Constructs a quaternion set to the identity quaternion.
   */
  public static Quaternion identity() {
    final Quaternion ret = new Quaternion();
    ret._qStorage[3] = 1.0;
    return ret;
  }

  /**
   * Constructs a quaternion from time derivative of [q] with angular
   * velocity [omega].
   */
  public static Quaternion dq(Quaternion q, Vector3 omega) {
    final Quaternion ret = new Quaternion();
    ret.setDQ(q, omega);
    return ret;
  }

  /**
   * Access the internal [storage] of the quaternions components.
   */
  public double[] getStorage() {
    return _qStorage;
  }

  /**
   * Access the [x] component of the quaternion.
   */
  public double getX() {
    return _qStorage[0];
  }

  public void setX(double x) {
    _qStorage[0] = x;
  }

  /**
   * Access the [y] component of the quaternion.
   */
  public double getY() {
    return _qStorage[1];
  }

  public void setY(double y) {
    _qStorage[1] = y;
  }

  /**
   * Access the [z] component of the quaternion.
   */
  public double getZ() {
    return _qStorage[2];
  }

  public void setZ(double z) {
    _qStorage[2] = z;
  }

  /**
   * Access the [w] component of the quaternion.
   */
  public double getW() {
    return _qStorage[3];
  }

  public void setW(double w) {
    _qStorage[3] = w;
  }

  /**
   * Constructs a quaternion from [yaw], [pitch] and [roll].
   */
  public Quaternion euler(double yaw, double pitch, double roll) {
    final Quaternion ret = new Quaternion();
    ret.setEuler(yaw, pitch, roll);
    return ret;
  }

  /**
   * Returns a new copy of [this].
   */
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Quaternion clone() {
    return new Quaternion(this);
  }

  /**
   * Copy [source] into [this].
   */
  public void setFrom(Quaternion source) {
    final double[] sourceStorage = source._qStorage;
    _qStorage[0] = sourceStorage[0];
    _qStorage[1] = sourceStorage[1];
    _qStorage[2] = sourceStorage[2];
    _qStorage[3] = sourceStorage[3];
  }

  /**
   * Set the quaternion to the raw values [x], [y], [z], and [w].
   */
  public void setValues(double x, double y, double z, double w) {
    _qStorage[0] = x;
    _qStorage[1] = y;
    _qStorage[2] = z;
    _qStorage[3] = w;
  }

  /**
   * Set the quaternion with rotation of [radians] around [axis].
   */
  public void setAxisAngle(Vector3 axis, double radians) {
    final double len = axis.getLength();
    if (len == 0.0) {
      return;
    }
    final double halfSin = Math.sin(radians * 0.5) / len;
    final double[] axisStorage = axis.getStorage();
    _qStorage[0] = axisStorage[0] * halfSin;
    _qStorage[1] = axisStorage[1] * halfSin;
    _qStorage[2] = axisStorage[2] * halfSin;
    _qStorage[3] = Math.cos(radians * 0.5);
  }

  public void setFromTwoVectors(Vector3 a, Vector3 b) {
    final Vector3 v1 = a.normalized();
    final Vector3 v2 = b.normalized();

    final double c = v1.dot(v2);
    double angle = Math.acos(c);
    Vector3 axis = v1.cross(v2);

    if (Math.abs(1.0 + c) < 0.0005) {
      // c \approx -1 indicates 180 degree rotation
      angle = Math.PI;

      // a and b are parallel in opposite directions. We need any
      // vector as our rotation axis that is perpendicular.
      // Find one by taking the cross product of v1 with an appropriate unit axis
      if (v1.getX() > v1.getY() && v1.getX() > v1.getZ()) {
        // v1 points in a dominantly x direction, so don't cross with that axis
        axis = v1.cross(new Vector3(0.0, 1.0, 0.0));
      }
      else {
        // Predominantly points in some other direction, so x-axis should be safe
        axis = v1.cross(new Vector3(1.0, 0.0, 0.0));
      }
    }
    else if (Math.abs(1.0 - c) < 0.0005) {
      // c \approx 1 is 0-degree rotation, axis is arbitrary
      angle = 0.0;
      axis = new Vector3(1.0, 0.0, 0.0);
    }

    setAxisAngle(axis.normalized(), angle);
  }

  /**
   * Set the quaternion to the time derivative of [q] with angular velocity
   * [omega].
   */
  public void setDQ(Quaternion q, Vector3 omega) {
    final double[] qStorage = q._qStorage;
    final double[] omegaStorage = omega.getStorage();
    final double qx = qStorage[0];
    final double qy = qStorage[1];
    final double qz = qStorage[2];
    final double qw = qStorage[3];
    final double ox = omegaStorage[0];
    final double oy = omegaStorage[1];
    final double oz = omegaStorage[2];
    final double _x = ox * qw + oy * qz - oz * qy;
    final double _y = oy * qw + oz * qx - ox * qz;
    final double _z = oz * qw + ox * qy - oy * qx;
    final double _w = -ox * qx - oy * qy - oz * qz;
    _qStorage[0] = _x * 0.5;
    _qStorage[1] = _y * 0.5;
    _qStorage[2] = _z * 0.5;
    _qStorage[3] = _w * 0.5;
  }

  /**
   * Set quaternion with rotation of [yaw], [pitch] and [roll].
   */
  public void setEuler(double yaw, double pitch, double roll) {
    final double halfYaw = yaw * 0.5;
    final double halfPitch = pitch * 0.5;
    final double halfRoll = roll * 0.5;
    final double cosYaw = Math.cos(halfYaw);
    final double sinYaw = Math.sin(halfYaw);
    final double cosPitch = Math.cos(halfPitch);
    final double sinPitch = Math.sin(halfPitch);
    final double cosRoll = Math.cos(halfRoll);
    final double sinRoll = Math.sin(halfRoll);
    _qStorage[0] = cosRoll * sinPitch * cosYaw + sinRoll * cosPitch * sinYaw;
    _qStorage[1] = cosRoll * cosPitch * sinYaw - sinRoll * sinPitch * cosYaw;
    _qStorage[2] = sinRoll * cosPitch * cosYaw - cosRoll * sinPitch * sinYaw;
    _qStorage[3] = cosRoll * cosPitch * cosYaw + sinRoll * sinPitch * sinYaw;
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
    _qStorage[0] *= d;
    _qStorage[1] *= d;
    _qStorage[2] *= d;
    _qStorage[3] *= d;
    return l;
  }

  /**
   * Conjugate [this].
   */
  public void conjugate() {
    _qStorage[2] = -_qStorage[2];
    _qStorage[1] = -_qStorage[1];
    _qStorage[0] = -_qStorage[0];
  }

  /**
   * Invert [this].
   */
  public void inverse() {
    final double l = 1.0 / getLength2();
    _qStorage[3] = _qStorage[3] * l;
    _qStorage[2] = -_qStorage[2] * l;
    _qStorage[1] = -_qStorage[1] * l;
    _qStorage[0] = -_qStorage[0] * l;
  }

  /**
   * Normalized copy of [this].
   */
  public Quaternion normalized() {
    final Quaternion ret = clone();
    ret.normalize();
    return ret;
  }

  /**
   * Conjugated copy of [this].
   */
  public Quaternion conjugated() {
    final Quaternion ret = clone();
    ret.conjugate();
    return ret;
  }

  /**
   * Inverted copy of [this].
   */
  public Quaternion inverted() {
    final Quaternion ret = clone();
    ret.inverse();
    return ret;
  }

  /**
   * [radians] of rotation around the [axis] of the rotation.
   */
  public double getRadians() {
    return 2.0 * Math.acos(_qStorage[3]);
  }

  /**
   * [axis] of rotation.
   */
  public Vector3 getAxis() {
    final double den = 1.0 - (_qStorage[3] * _qStorage[3]);
    if (den < 0.0005) {
      // 0-angle rotation, so axis does not matter
      return new Vector3();
    }

    final double scale = 1.0 / Math.sqrt(den);
    return new Vector3(
      _qStorage[0] * scale, _qStorage[1] * scale, _qStorage[2] * scale);
  }

  /**
   * Length squared.
   */
  public double getLength2() {
    final double x = _qStorage[0];
    final double y = _qStorage[1];
    final double z = _qStorage[2];
    final double w = _qStorage[3];
    return (x * x) + (y * y) + (z * z) + (w * w);
  }

  /**
   * Length.
   */
  public double getLength() {
    return Math.sqrt(getLength2());
  }

  /**
   * Returns a copy of [v] rotated by quaternion.
   */
  public Vector3 rotated(Vector3 v) {
    final Vector3 out = v.clone();
    rotate(out);
    return out;
  }

  /**
   * Rotates [v] by [this].
   */
  public Vector3 rotate(Vector3 v) {
    // conjugate(this) * [v,0] * this
    final double _w = _qStorage[3];
    final double _z = _qStorage[2];
    final double _y = _qStorage[1];
    final double _x = _qStorage[0];
    final double tiw = _w;
    final double tiz = -_z;
    final double tiy = -_y;
    final double tix = -_x;
    final double tx = tiw * v.getX() + tix * 0.0 + tiy * v.getZ() - tiz * v.getY();
    final double ty = tiw * v.getY() + tiy * 0.0 + tiz * v.getX() - tix * v.getZ();
    final double tz = tiw * v.getZ() + tiz * 0.0 + tix * v.getY() - tiy * v.getX();
    final double tw = tiw * 0.0 - tix * v.getX() - tiy * v.getY() - tiz * v.getZ();
    final double result_x = tw * _x + tx * _w + ty * _z - tz * _y;
    final double result_y = tw * _y + ty * _w + tz * _x - tx * _z;
    final double result_z = tw * _z + tz * _w + tx * _y - ty * _x;
    final double[] vStorage = v.getStorage();
    vStorage[2] = result_z;
    vStorage[1] = result_y;
    vStorage[0] = result_x;
    return v;
  }

  /**
   * Add [arg] to [this].
   */
  public void add(Quaternion arg) {
    final double[] argStorage = arg._qStorage;
    _qStorage[0] = _qStorage[0] + argStorage[0];
    _qStorage[1] = _qStorage[1] + argStorage[1];
    _qStorage[2] = _qStorage[2] + argStorage[2];
    _qStorage[3] = _qStorage[3] + argStorage[3];
  }

  /**
   * Subtracts [arg] from [this].
   */
  public void sub(Quaternion arg) {
    final double[] argStorage = arg._qStorage;
    _qStorage[0] = _qStorage[0] - argStorage[0];
    _qStorage[1] = _qStorage[1] - argStorage[1];
    _qStorage[2] = _qStorage[2] - argStorage[2];
    _qStorage[3] = _qStorage[3] - argStorage[3];
  }

  /**
   * Scales [this] by [scale].
   */
  public void scale(double scale) {
    _qStorage[3] = _qStorage[3] * scale;
    _qStorage[2] = _qStorage[2] * scale;
    _qStorage[1] = _qStorage[1] * scale;
    _qStorage[0] = _qStorage[0] * scale;
  }

  /**
   * Scaled copy of [this].
   */
  public Quaternion scaled(double scale) {
    final Quaternion ret = clone();
    ret.scale(scale);
    return ret;
  }

  /**
   * [this] rotated by [other].
   */
  public Quaternion operatorMultiply(Quaternion other) {
    final double _w = _qStorage[3];
    final double _z = _qStorage[2];
    final double _y = _qStorage[1];
    final double _x = _qStorage[0];
    final double[] otherStorage = other._qStorage;
    final double ow = otherStorage[3];
    final double oz = otherStorage[2];
    final double oy = otherStorage[1];
    final double ox = otherStorage[0];
    return new Quaternion(
      _w * ox + _x * ow + _y * oz - _z * oy,
      _w * oy + _y * ow + _z * ox - _x * oz,
      _w * oz + _z * ow + _x * oy - _y * ox,
      _w * ow - _x * ox - _y * oy - _z * oz);
  }

  /**
   * Returns copy of [this] + [other].
   */
  public Quaternion operatorAdd(Quaternion other) {
    final Quaternion ret = clone();
    ret.add(other);
    return ret;
  }

  /**
   * Returns copy of [this] - [other].
   */
  public Quaternion operatorSub(Quaternion other) {
    final Quaternion ret = clone();
    ret.sub(other);
    return ret;
  }

  /**
   * Returns negated copy of [this].
   */
  public Quaternion operatorConjugated() {
    final Quaternion ret = clone();
    ret.conjugated();
    return ret;
  }

  /**
   * Printable string.
   */
  @Override()
  public String toString() {
    return "" + _qStorage[0] + ", " + _qStorage[1] + ", " + _qStorage[2] + " @ " + _qStorage[3];
  }

  /**
   * Relative error between [this] and [correct].
   */
  public double relativeError(Quaternion correct) {
    final Quaternion diff = correct.operatorSub(this);
    final double norm_diff = diff.getLength();
    final double correct_norm = correct.getLength();
    return norm_diff / correct_norm;
  }

  /**
   * Absolute error between [this] and [correct].
   */
  public double absoluteError(Quaternion correct) {
    final double this_norm = getLength();
    final double correct_norm = correct.getLength();
    final double norm_diff = Math.abs(this_norm - correct_norm);
    return norm_diff;
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

import java.util.Arrays;

/**
 * 4D Matrix.
 * <p>
 * Values are stored in column major order.
 * <p>
 * This code is ported from the Matrix4 class in the Dart vector_math
 * package. The Class is ported as is without concern for making the code
 * consistent with Java api conventions to keep the code consistent with
 * the Dart code to simplify using Transform Matrixes returned by Flutter.
 */
@SuppressWarnings({"Duplicates", "PointlessArithmeticExpression", "UnnecessaryLocalVariable", "JoinDeclarationAndAssignmentJava"})
public class Matrix4 {
  final double[] _m4storage;

  /**
   * Constructs a new mat4.
   */
  public Matrix4(
    double arg0,
    double arg1,
    double arg2,
    double arg3,
    double arg4,
    double arg5,
    double arg6,
    double arg7,
    double arg8,
    double arg9,
    double arg10,
    double arg11,
    double arg12,
    double arg13,
    double arg14,
    double arg15) {
    _m4storage = new double[16];
    setValues(arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9,
              arg10, arg11, arg12, arg13, arg14, arg15);
  }

  /**
   * Zero matrix.
   */
  Matrix4() {
    _m4storage = new double[16];
  }

  /**
   * Constructs Matrix4 with given [double[]] as [storage].
   */
  public Matrix4(double[] storage) {
    this._m4storage = storage;
  }

  /**
   * Solve [A] * [x] = [b].
   */
  static void solve2(Matrix4 A, Vector2 x, Vector2 b) {
    final double a11 = A.entry(0, 0);
    final double a12 = A.entry(0, 1);
    final double a21 = A.entry(1, 0);
    final double a22 = A.entry(1, 1);
    final double bx = b.getX() - A._m4storage[8];
    final double by = b.getY() - A._m4storage[9];
    double det = a11 * a22 - a12 * a21;

    if (det != 0.0) {
      det = 1.0 / det;
    }

    x.setX(det * (a22 * bx - a12 * by));
    x.setY(det * (a11 * by - a21 * bx));
  }

  /**
   * Solve [A] * [x] = [b].
   */
  public static void solve3(Matrix4 A, Vector3 x, Vector3 b) {
    final double A0x = A.entry(0, 0);
    final double A0y = A.entry(1, 0);
    final double A0z = A.entry(2, 0);
    final double A1x = A.entry(0, 1);
    final double A1y = A.entry(1, 1);
    final double A1z = A.entry(2, 1);
    final double A2x = A.entry(0, 2);
    final double A2y = A.entry(1, 2);
    final double A2z = A.entry(2, 2);
    final double bx = b.getX() - A._m4storage[12];
    final double by = b.getY() - A._m4storage[13];
    final double bz = b.getZ() - A._m4storage[14];
    double rx, ry, rz;
    double det;

    // Column1 cross Column 2
    rx = A1y * A2z - A1z * A2y;
    ry = A1z * A2x - A1x * A2z;
    rz = A1x * A2y - A1y * A2x;

    // A.getColumn(0).dot(x)
    det = A0x * rx + A0y * ry + A0z * rz;
    if (det != 0.0) {
      det = 1.0 / det;
    }

    // b dot [Column1 cross Column 2]
    final double x_ = det * (bx * rx + by * ry + bz * rz);

    // Column2 cross b
    rx = -(A2y * bz - A2z * by);
    ry = -(A2z * bx - A2x * bz);
    rz = -(A2x * by - A2y * bx);
    // Column0 dot -[Column2 cross b (Column3)]
    final double y_ = det * (A0x * rx + A0y * ry + A0z * rz);

    // b cross Column 1
    rx = -(by * A1z - bz * A1y);
    ry = -(bz * A1x - bx * A1z);
    rz = -(bx * A1y - by * A1x);
    // Column0 dot -[b cross Column 1]
    final double z_ = det * (A0x * rx + A0y * ry + A0z * rz);

    x.setX(x_);
    x.setY(y_);
    x.setZ(z_);
  }

  /**
   * Solve [A] * [x] = [b].
   */
  static void solve(Matrix4 A, Vector4 x, Vector4 b) {
    final double a00 = A._m4storage[0];
    final double a01 = A._m4storage[1];
    final double a02 = A._m4storage[2];
    final double a03 = A._m4storage[3];
    final double a10 = A._m4storage[4];
    final double a11 = A._m4storage[5];
    final double a12 = A._m4storage[6];
    final double a13 = A._m4storage[7];
    final double a20 = A._m4storage[8];
    final double a21 = A._m4storage[9];
    final double a22 = A._m4storage[10];
    final double a23 = A._m4storage[11];
    final double a30 = A._m4storage[12];
    final double a31 = A._m4storage[13];
    final double a32 = A._m4storage[14];
    final double a33 = A._m4storage[15];
    final double b00 = a00 * a11 - a01 * a10;
    final double b01 = a00 * a12 - a02 * a10;
    final double b02 = a00 * a13 - a03 * a10;
    final double b03 = a01 * a12 - a02 * a11;
    final double b04 = a01 * a13 - a03 * a11;
    final double b05 = a02 * a13 - a03 * a12;
    final double b06 = a20 * a31 - a21 * a30;
    final double b07 = a20 * a32 - a22 * a30;
    final double b08 = a20 * a33 - a23 * a30;
    final double b09 = a21 * a32 - a22 * a31;
    final double b10 = a21 * a33 - a23 * a31;
    final double b11 = a22 * a33 - a23 * a32;

    final double bX = b.getStorage()[0];
    final double bY = b.getStorage()[1];
    final double bZ = b.getStorage()[2];
    final double bW = b.getStorage()[3];

    double det =
      b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;

    if (det != 0.0) {
      det = 1.0 / det;
    }

    x
      .setX(det *
            ((a11 * b11 - a12 * b10 + a13 * b09) * bX -
             (a10 * b11 - a12 * b08 + a13 * b07) * bY +
             (a10 * b10 - a11 * b08 + a13 * b06) * bZ -
             (a10 * b09 - a11 * b07 + a12 * b06) * bW));
    x.setY(det *
           -((a01 * b11 - a02 * b10 + a03 * b09) * bX -
             (a00 * b11 - a02 * b08 + a03 * b07) * bY +
             (a00 * b10 - a01 * b08 + a03 * b06) * bZ -
             (a00 * b09 - a01 * b07 + a02 * b06) * bW));
    x.setZ(det *
           ((a31 * b05 - a32 * b04 + a33 * b03) * bX -
            (a30 * b05 - a32 * b02 + a33 * b01) * bY +
            (a30 * b04 - a31 * b02 + a33 * b00) * bZ -
            (a30 * b03 - a31 * b01 + a32 * b00) * bW));
    x.setW(det *
           -((a21 * b05 - a22 * b04 + a23 * b03) * bX -
             (a20 * b05 - a22 * b02 + a23 * b01) * bY +
             (a20 * b04 - a21 * b02 + a23 * b00) * bZ -
             (a20 * b03 - a21 * b01 + a22 * b00) * bW));
  }

  /**
   * Returns a matrix that is the inverse of [other] if [other] is invertible,
   * otherwise `null`.
   */
  static Matrix4 tryInvert(Matrix4 other) {
    final Matrix4 r = new Matrix4();
    final double determinant = r.copyInverse(other);
    if (determinant == 0.0) {
      return null;
    }
    return r;
  }

  /**
   * New matrix from [values].
   */
  public static Matrix4 fromList(double[] values) {
    final Matrix4 ret = new Matrix4();
    ret.setValues(
      values[0],
      values[1],
      values[2],
      values[3],
      values[4],
      values[5],
      values[6],
      values[7],
      values[8],
      values[9],
      values[10],
      values[11],
      values[12],
      values[13],
      values[14],
      values[15]);
    return ret;
  }

  public static Matrix4 zero() {
    return new Matrix4();
  }

  /**
   * Identity matrix.
   */
  public static Matrix4 identity() {
    final Matrix4 ret = new Matrix4();
    ret.setIdentity();
    return ret;
  }

  /**
   * Copies values from [other].
   */
  public static Matrix4 copy(Matrix4 other) {
    final Matrix4 ret = new Matrix4();
    ret.setFrom(other);
    return ret;
  }

  /**
   * Constructs a matrix that is the inverse of [other].
   */
  public static Matrix4 inverted(Matrix4 other) {
    final Matrix4 r = new Matrix4();
    final double determinant = r.copyInverse(other);
    if (determinant == 0.0) {
      throw new IllegalArgumentException(
        "" + other + "other Matrix cannot be inverted");
    }
    return r;
  }

  /**
   * Constructs a new mat4 from columns.
   */
  public static Matrix4 columns(
    Vector4 arg0, Vector4 arg1, Vector4 arg2, Vector4 arg3) {
    final Matrix4 ret = new Matrix4();
    ret.setColumns(arg0, arg1, arg2, arg3);
    return ret;
  }

  /**
   * Outer product of [u] and [v].
   */
  public static Matrix4 outer(Vector4 u, Vector4 v) {
    final Matrix4 ret = new Matrix4();
    ret.setOuter(u, v);
    return ret;
  }

  /**
   * Rotation of [radians_] around X.
   */
  public static Matrix4 rotationX(double radians) {
    final Matrix4 ret = new Matrix4();
    ret._m4storage[15] = 1.0;
    ret.setRotationX(radians);
    return ret;
  }

  /**
   * Rotation of [radians_] around Y.
   */
  public static Matrix4 rotationY(double radians) {
    final Matrix4 ret = new Matrix4();
    ret._m4storage[15] = 1.0;
    ret.setRotationY(radians);
    return ret;
  }

  /**
   * Rotation of [radians_] around Z.
   */
  public static Matrix4 rotationZ(double radians) {
    final Matrix4 ret = new Matrix4();
    ret._m4storage[15] = 1.0;
    ret.setRotationZ(radians);
    return ret;
  }

  /**
   * Translation matrix.
   */
  public static Matrix4 translation(Vector3 translation) {
    final Matrix4 ret = new Matrix4();
    ret.setIdentity();
    ret.setTranslation(translation);
    return ret;
  }

  /**
   * Translation matrix.
   */
  public static Matrix4 translationValues(double x, double y, double z) {
    final Matrix4 ret = new Matrix4();
    ret.setIdentity();
    ret.setTranslationRaw(x, y, z);
    return ret;
  }

  /**
   * Scale matrix.
   */
  public static Matrix4 diagonal3(Vector3 scale) {
    final Matrix4 m = new Matrix4();
    final double[] mStorage = m._m4storage;
    final double[] scaleStorage = scale._v3storage;
    mStorage[15] = 1.0;
    mStorage[10] = scaleStorage[2];
    mStorage[5] = scaleStorage[1];
    mStorage[0] = scaleStorage[0];
    return m;
  }

  /**
   * Scale matrix.
   */
  public static Matrix4 diagonal3Values(double x, double y, double z) {
    final Matrix4 ret = new Matrix4();
    ret._m4storage[15] = 1.0;
    ret._m4storage[10] = z;
    ret._m4storage[5] = y;
    ret._m4storage[0] = x;
    return ret;
  }

  /**
   * Skew matrix around X axis
   */
  public static Matrix4 skewX(double alpha) {
    final Matrix4 m = Matrix4.identity();
    m._m4storage[4] = Math.tan(alpha);
    return m;
  }

  /**
   * Skew matrix around Y axis.
   */
  public static Matrix4 skewY(double beta) {
    final Matrix4 m = Matrix4.identity();
    m._m4storage[1] = Math.tan(beta);
    return m;
  }

  /**
   * Skew matrix around X axis (alpha) and Y axis (beta).
   */
  public static Matrix4 skew(double alpha, double beta) {
    final Matrix4 m = Matrix4.identity();
    m._m4storage[1] = Math.tan(beta);
    m._m4storage[4] = Math.tan(alpha);
    return m;
  }

  /**
   * Constructs Matrix4 from [translation], [rotation] and [scale].
   */
  public static Matrix4 compose(
    Vector3 translation, Quaternion rotation, Vector3 scale) {
    final Matrix4 matrix = new Matrix4();
    matrix.setFromTranslationRotationScale(translation, rotation, scale);
    return matrix;
  }

  /**
   * The components of the matrix.
   */
  double[] getStorage() {
    return _m4storage;
  }

  /**
   * Return index in storage for [row], [col] value.
   */
  public int index(int row, int col) {
    return (col * 4) + row;
  }

  /**
   * Value at [row], [col].
   */
  public double entry(int row, int col) {
    assert ((row >= 0) && (row < getDimension()));
    assert ((col >= 0) && (col < getDimension()));

    return _m4storage[index(row, col)];
  }

  /**
   * Set value at [row], [col] to be [v].
   */
  void setEntry(int row, int col, double v) {
    assert ((row >= 0) && (row < getDimension()));
    assert ((col >= 0) && (col < getDimension()));

    _m4storage[index(row, col)] = v;
  }

  /**
   * Sets the diagonal to [arg].
   */
  void splatDiagonal(double arg) {
    _m4storage[0] = arg;
    _m4storage[5] = arg;
    _m4storage[10] = arg;
    _m4storage[15] = arg;
  }

  /**
   * Sets the matrix with specified values.
   */
  void setValues(
    double arg0,
    double arg1,
    double arg2,
    double arg3,
    double arg4,
    double arg5,
    double arg6,
    double arg7,
    double arg8,
    double arg9,
    double arg10,
    double arg11,
    double arg12,
    double arg13,
    double arg14,
    double arg15) {
    _m4storage[15] = arg15;
    _m4storage[14] = arg14;
    _m4storage[13] = arg13;
    _m4storage[12] = arg12;
    _m4storage[11] = arg11;
    _m4storage[10] = arg10;
    _m4storage[9] = arg9;
    _m4storage[8] = arg8;
    _m4storage[7] = arg7;
    _m4storage[6] = arg6;
    _m4storage[5] = arg5;
    _m4storage[4] = arg4;
    _m4storage[3] = arg3;
    _m4storage[2] = arg2;
    _m4storage[1] = arg1;
    _m4storage[0] = arg0;
  }

  /**
   * Sets the entire matrix to the column values.
   */
  void setColumns(Vector4 arg0, Vector4 arg1, Vector4 arg2, Vector4 arg3) {
    final double[] arg0Storage = arg0._v4storage;
    final double[] arg1Storage = arg1._v4storage;
    final double[] arg2Storage = arg2._v4storage;
    final double[] arg3Storage = arg3._v4storage;
    _m4storage[0] = arg0Storage[0];
    _m4storage[1] = arg0Storage[1];
    _m4storage[2] = arg0Storage[2];
    _m4storage[3] = arg0Storage[3];
    _m4storage[4] = arg1Storage[0];
    _m4storage[5] = arg1Storage[1];
    _m4storage[6] = arg1Storage[2];
    _m4storage[7] = arg1Storage[3];
    _m4storage[8] = arg2Storage[0];
    _m4storage[9] = arg2Storage[1];
    _m4storage[10] = arg2Storage[2];
    _m4storage[11] = arg2Storage[3];
    _m4storage[12] = arg3Storage[0];
    _m4storage[13] = arg3Storage[1];
    _m4storage[14] = arg3Storage[2];
    _m4storage[15] = arg3Storage[3];
  }

  /**
   * Sets the entire matrix to the matrix in [arg].
   */
  void setFrom(Matrix4 arg) {
    final double[] argStorage = arg._m4storage;
    _m4storage[15] = argStorage[15];
    _m4storage[14] = argStorage[14];
    _m4storage[13] = argStorage[13];
    _m4storage[12] = argStorage[12];
    _m4storage[11] = argStorage[11];
    _m4storage[10] = argStorage[10];
    _m4storage[9] = argStorage[9];
    _m4storage[8] = argStorage[8];
    _m4storage[7] = argStorage[7];
    _m4storage[6] = argStorage[6];
    _m4storage[5] = argStorage[5];
    _m4storage[4] = argStorage[4];
    _m4storage[3] = argStorage[3];
    _m4storage[2] = argStorage[2];
    _m4storage[1] = argStorage[1];
    _m4storage[0] = argStorage[0];
  }

  /**
   * Sets the matrix from translation [arg0] and rotation [arg1].
   */
  void setFromTranslationRotation(Vector3 arg0, Quaternion arg1) {
    final double[] arg1Storage = arg1._qStorage;
    final double x = arg1Storage[0];
    final double y = arg1Storage[1];
    final double z = arg1Storage[2];
    final double w = arg1Storage[3];
    final double x2 = x + x;
    final double y2 = y + y;
    final double z2 = z + z;
    final double xx = x * x2;
    final double xy = x * y2;
    final double xz = x * z2;
    final double yy = y * y2;
    final double yz = y * z2;
    final double zz = z * z2;
    final double wx = w * x2;
    final double wy = w * y2;
    final double wz = w * z2;

    final double[] arg0Storage = arg0._v3storage;
    _m4storage[0] = 1.0 - (yy + zz);
    _m4storage[1] = xy + wz;
    _m4storage[2] = xz - wy;
    _m4storage[3] = 0.0;
    _m4storage[4] = xy - wz;
    _m4storage[5] = 1.0 - (xx + zz);
    _m4storage[6] = yz + wx;
    _m4storage[7] = 0.0;
    _m4storage[8] = xz + wy;
    _m4storage[9] = yz - wx;
    _m4storage[10] = 1.0 - (xx + yy);
    _m4storage[11] = 0.0;
    _m4storage[12] = arg0Storage[0];
    _m4storage[13] = arg0Storage[1];
    _m4storage[14] = arg0Storage[2];
    _m4storage[15] = 1.0;
  }

  /**
   * Sets the matrix from [translation], [rotation] and [scale].
   */
  void setFromTranslationRotationScale(
    Vector3 translation, Quaternion rotation, Vector3 scale) {
    setFromTranslationRotation(translation, rotation);
    this.scale(scale);
  }

  /**
   * Sets the diagonal of the matrix to be [arg].
   */
  void setDiagonal(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    _m4storage[0] = argStorage[0];
    _m4storage[5] = argStorage[1];
    _m4storage[10] = argStorage[2];
    _m4storage[15] = argStorage[3];
  }

  void setOuter(Vector4 u, Vector4 v) {
    final double[] uStorage = u._v4storage;
    final double[] vStorage = v._v4storage;
    _m4storage[0] = uStorage[0] * vStorage[0];
    _m4storage[1] = uStorage[0] * vStorage[1];
    _m4storage[2] = uStorage[0] * vStorage[2];
    _m4storage[3] = uStorage[0] * vStorage[3];
    _m4storage[4] = uStorage[1] * vStorage[0];
    _m4storage[5] = uStorage[1] * vStorage[1];
    _m4storage[6] = uStorage[1] * vStorage[2];
    _m4storage[7] = uStorage[1] * vStorage[3];
    _m4storage[8] = uStorage[2] * vStorage[0];
    _m4storage[9] = uStorage[2] * vStorage[1];
    _m4storage[10] = uStorage[2] * vStorage[2];
    _m4storage[11] = uStorage[2] * vStorage[3];
    _m4storage[12] = uStorage[3] * vStorage[0];
    _m4storage[13] = uStorage[3] * vStorage[1];
    _m4storage[14] = uStorage[3] * vStorage[2];
    _m4storage[15] = uStorage[3] * vStorage[3];
  }

  /**
   * Returns a printable string
   */
  @Override()
  public String toString() {
    return
      "[0] " + getRow(0) + "\n" +
      "[1] " + getRow(1) + "\n" +
      "[2] " + getRow(2) + "\n" +
      "[3] " + getRow(3) + "\n";
  }

  /**
   * Dimension of the matrix.
   */
  public int getDimension() {
    return 4;
  }

  /**
   * Access the element of the matrix at the index [i].
   */
  double get(int i) {
    return _m4storage[i];
  }

  /**
   * Set the element of the matrix at the index [i].
   */
  void set(int i, double v) {
    _m4storage[i] = v;
  }

  /**
   * Check if two matrices are the same.
   */
  @Override()
  public boolean equals(Object o) {
    if (!(o instanceof Matrix4)) {
      return false;
    }
    final Matrix4 other = (Matrix4)o;
    return (_m4storage[0] == other._m4storage[0]) &&
           (_m4storage[1] == other._m4storage[1]) &&
           (_m4storage[2] == other._m4storage[2]) &&
           (_m4storage[3] == other._m4storage[3]) &&
           (_m4storage[4] == other._m4storage[4]) &&
           (_m4storage[5] == other._m4storage[5]) &&
           (_m4storage[6] == other._m4storage[6]) &&
           (_m4storage[7] == other._m4storage[7]) &&
           (_m4storage[8] == other._m4storage[8]) &&
           (_m4storage[9] == other._m4storage[9]) &&
           (_m4storage[10] == other._m4storage[10]) &&
           (_m4storage[11] == other._m4storage[11]) &&
           (_m4storage[12] == other._m4storage[12]) &&
           (_m4storage[13] == other._m4storage[13]) &&
           (_m4storage[14] == other._m4storage[14]) &&
           (_m4storage[15] == other._m4storage[15]);
  }

  @Override()
  public int hashCode() {
    return Arrays.hashCode(_m4storage);
  }

  /**
   * Returns row 0
   */
  public Vector4 getRow0() {
    return getRow(0);
  }

  /**
   * Sets row 0 to [arg]
   */
  public void setRow0(Vector4 arg) {
    setRow(0, arg);
  }

  /**
   * Returns row 1
   */
  public Vector4 getRow1() {
    return getRow(1);
  }

  /**
   * Sets row 1 to [arg]
   */
  public void setRow1(Vector4 arg) {
    setRow(1, arg);
  }

  /**
   * Returns row 2
   */
  public Vector4 getRow2() {
    return getRow(2);
  }

  /**
   * Sets row 2 to [arg]
   */
  public void setRow2(Vector4 arg) {
    setRow(2, arg);
  }

  /**
   * Returns row 3
   */
  public Vector4 getRow3() {
    return getRow(3);
  }

  /**
   * Sets row 3 to [arg]
   */
  public void setRow3(Vector4 arg) {
    setRow(3, arg);
  }

  /**
   * Assigns the [row] of the matrix [arg]
   */
  public void setRow(int row, Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    _m4storage[index(row, 0)] = argStorage[0];
    _m4storage[index(row, 1)] = argStorage[1];
    _m4storage[index(row, 2)] = argStorage[2];
    _m4storage[index(row, 3)] = argStorage[3];
  }

  /**
   * Gets the [row] of the matrix
   */
  public Vector4 getRow(int row) {
    final Vector4 r = new Vector4();
    final double[] rStorage = r._v4storage;
    rStorage[0] = _m4storage[index(row, 0)];
    rStorage[1] = _m4storage[index(row, 1)];
    rStorage[2] = _m4storage[index(row, 2)];
    rStorage[3] = _m4storage[index(row, 3)];
    return r;
  }

  /**
   * Assigns the [column] of the matrix [arg]
   */
  public void setColumn(int column, Vector4 arg) {
    final int entry = column * 4;
    final double[] argStorage = arg._v4storage;
    _m4storage[entry + 3] = argStorage[3];
    _m4storage[entry + 2] = argStorage[2];
    _m4storage[entry + 1] = argStorage[1];
    _m4storage[entry + 0] = argStorage[0];
  }

  /**
   * Gets the [column] of the matrix
   */
  public Vector4 getColumn(int column) {
    final Vector4 r = new Vector4();
    final double[] rStorage = r._v4storage;
    final int entry = column * 4;
    rStorage[3] = _m4storage[entry + 3];
    rStorage[2] = _m4storage[entry + 2];
    rStorage[1] = _m4storage[entry + 1];
    rStorage[0] = _m4storage[entry + 0];
    return r;
  }

  /**
   * Clone matrix.
   */
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Matrix4 clone() {
    return Matrix4.copy(this);
  }

  /**
   * Copy into [arg].
   */
  public Matrix4 copyInto(Matrix4 arg) {
    final double[] argStorage = arg._m4storage;
    argStorage[0] = _m4storage[0];
    argStorage[1] = _m4storage[1];
    argStorage[2] = _m4storage[2];
    argStorage[3] = _m4storage[3];
    argStorage[4] = _m4storage[4];
    argStorage[5] = _m4storage[5];
    argStorage[6] = _m4storage[6];
    argStorage[7] = _m4storage[7];
    argStorage[8] = _m4storage[8];
    argStorage[9] = _m4storage[9];
    argStorage[10] = _m4storage[10];
    argStorage[11] = _m4storage[11];
    argStorage[12] = _m4storage[12];
    argStorage[13] = _m4storage[13];
    argStorage[14] = _m4storage[14];
    argStorage[15] = _m4storage[15];
    return arg;
  }

  /**
   * Returns new matrix -this
   */
  public Matrix4 operatorNegate() {
    final Matrix4 ret = clone();
    ret.negate();
    return ret;
  }

  /**
   * Returns a new vector or matrix by multiplying [this] with [arg].
   */
  Matrix4 operatorMultiply(double arg) {
    return scaled(arg);
  }

  public Vector4 operatorMultiply(Vector4 arg) {
    return transformed(arg);
  }

  public Vector3 operatorMultiply(Vector3 arg) {
    return transformed3(arg);
  }

  public Matrix4 operatorMultiply(Matrix4 arg) {
    return multiplied(arg);
  }

  /**
   * Returns new matrix after component wise [this] + [arg]
   */
  public Matrix4 operatorAdd(Matrix4 arg) {
    final Matrix4 ret = clone();
    ret.add(arg);
    return ret;
  }

  /**
   * Returns new matrix after component wise [this] - [arg]
   */
  public Matrix4 operatorSub(Matrix4 arg) {
    final Matrix4 ret = clone();
    ret.sub(arg);
    return ret;
  }

  /**
   * Translate this matrix by a [Vector3], [Vector4], or x,y,z
   */
  public void translate(double x) {
    translate(x, 0, 0);
  }

  public void translate(Vector3 v) {
    translate(v.getX(), v.getY(), v.getZ());
  }

  public void translate(Vector4 v) {
    translate(v.getX(), v.getY(), v.getZ(), v.getW());
  }

  public void translate(double x, double y, double z) {
    translate(x, y, z, 1);
  }

  public void translate(double tx, double ty, double tz, double tw) {
    final double t1 = _m4storage[0] * tx +
                      _m4storage[4] * ty +
                      _m4storage[8] * tz +
                      _m4storage[12] * tw;
    final double t2 = _m4storage[1] * tx +
                      _m4storage[5] * ty +
                      _m4storage[9] * tz +
                      _m4storage[13] * tw;
    final double t3 = _m4storage[2] * tx +
                      _m4storage[6] * ty +
                      _m4storage[10] * tz +
                      _m4storage[14] * tw;
    final double t4 = _m4storage[3] * tx +
                      _m4storage[7] * ty +
                      _m4storage[11] * tz +
                      _m4storage[15] * tw;
    _m4storage[12] = t1;
    _m4storage[13] = t2;
    _m4storage[14] = t3;
    _m4storage[15] = t4;
  }

  /**
   * Multiply [this] by a translation from the left.
   */
  public void leftTranslate(double x, double y, double z) {
    leftTranslater(x, y, z, 1);
  }

  public void leftTranslate(double x) {
    leftTranslater(x, 0, 0, 1);
  }

  public void leftTranslate(Vector4 v) {
    leftTranslater(v.getX(), v.getY(), v.getZ(), v.getW());
  }

  public void leftTranslate(Vector3 v) {
    leftTranslater(v.getX(), v.getY(), v.getZ(), 1.0);
  }

  void leftTranslater(double tx, double ty, double tz, double tw) {
    // Column 1
    _m4storage[0] += tx * _m4storage[3];
    _m4storage[1] += ty * _m4storage[3];
    _m4storage[2] += tz * _m4storage[3];
    _m4storage[3] = tw * _m4storage[3];

    // Column 2
    _m4storage[4] += tx * _m4storage[7];
    _m4storage[5] += ty * _m4storage[7];
    _m4storage[6] += tz * _m4storage[7];
    _m4storage[7] = tw * _m4storage[7];

    // Column 3
    _m4storage[8] += tx * _m4storage[11];
    _m4storage[9] += ty * _m4storage[11];
    _m4storage[10] += tz * _m4storage[11];
    _m4storage[11] = tw * _m4storage[11];

    // Column 4
    _m4storage[12] += tx * _m4storage[15];
    _m4storage[13] += ty * _m4storage[15];
    _m4storage[14] += tz * _m4storage[15];
    _m4storage[15] = tw * _m4storage[15];
  }

  /**
   * Rotate this [angle] radians around [axis]
   */
  public void rotate(Vector3 axis, double angle) {
    final double len = axis.getLength();
    final double[] axisStorage = axis._v3storage;
    final double x = axisStorage[0] / len;
    final double y = axisStorage[1] / len;
    final double z = axisStorage[2] / len;
    final double c = Math.cos(angle);
    final double s = Math.sin(angle);
    final double C = 1.0 - c;
    final double m11 = x * x * C + c;
    final double m12 = x * y * C - z * s;
    final double m13 = x * z * C + y * s;
    final double m21 = y * x * C + z * s;
    final double m22 = y * y * C + c;
    final double m23 = y * z * C - x * s;
    final double m31 = z * x * C - y * s;
    final double m32 = z * y * C + x * s;
    final double m33 = z * z * C + c;
    final double t1 =
      _m4storage[0] * m11 + _m4storage[4] * m21 + _m4storage[8] * m31;
    final double t2 =
      _m4storage[1] * m11 + _m4storage[5] * m21 + _m4storage[9] * m31;
    final double t3 =
      _m4storage[2] * m11 + _m4storage[6] * m21 + _m4storage[10] * m31;
    final double t4 =
      _m4storage[3] * m11 + _m4storage[7] * m21 + _m4storage[11] * m31;
    final double t5 =
      _m4storage[0] * m12 + _m4storage[4] * m22 + _m4storage[8] * m32;
    final double t6 =
      _m4storage[1] * m12 + _m4storage[5] * m22 + _m4storage[9] * m32;
    final double t7 =
      _m4storage[2] * m12 + _m4storage[6] * m22 + _m4storage[10] * m32;
    final double t8 =
      _m4storage[3] * m12 + _m4storage[7] * m22 + _m4storage[11] * m32;
    final double t9 =
      _m4storage[0] * m13 + _m4storage[4] * m23 + _m4storage[8] * m33;
    final double t10 =
      _m4storage[1] * m13 + _m4storage[5] * m23 + _m4storage[9] * m33;
    final double t11 =
      _m4storage[2] * m13 + _m4storage[6] * m23 + _m4storage[10] * m33;
    final double t12 =
      _m4storage[3] * m13 + _m4storage[7] * m23 + _m4storage[11] * m33;
    _m4storage[0] = t1;
    _m4storage[1] = t2;
    _m4storage[2] = t3;
    _m4storage[3] = t4;
    _m4storage[4] = t5;
    _m4storage[5] = t6;
    _m4storage[6] = t7;
    _m4storage[7] = t8;
    _m4storage[8] = t9;
    _m4storage[9] = t10;
    _m4storage[10] = t11;
    _m4storage[11] = t12;
  }

  /**
   * Rotate this [angle] radians around X
   */
  public void rotateX(double angle) {
    final double cosAngle = Math.cos(angle);
    final double sinAngle = Math.sin(angle);
    final double t1 = _m4storage[4] * cosAngle + _m4storage[8] * sinAngle;
    final double t2 = _m4storage[5] * cosAngle + _m4storage[9] * sinAngle;
    final double t3 = _m4storage[6] * cosAngle + _m4storage[10] * sinAngle;
    final double t4 = _m4storage[7] * cosAngle + _m4storage[11] * sinAngle;
    final double t5 = _m4storage[4] * -sinAngle + _m4storage[8] * cosAngle;
    final double t6 = _m4storage[5] * -sinAngle + _m4storage[9] * cosAngle;
    final double t7 = _m4storage[6] * -sinAngle + _m4storage[10] * cosAngle;
    final double t8 = _m4storage[7] * -sinAngle + _m4storage[11] * cosAngle;
    _m4storage[4] = t1;
    _m4storage[5] = t2;
    _m4storage[6] = t3;
    _m4storage[7] = t4;
    _m4storage[8] = t5;
    _m4storage[9] = t6;
    _m4storage[10] = t7;
    _m4storage[11] = t8;
  }

  /**
   * Rotate this matrix [angle] radians around Y
   */
  public void rotateY(double angle) {
    final double cosAngle = Math.cos(angle);
    final double sinAngle = Math.sin(angle);
    final double t1 = _m4storage[0] * cosAngle + _m4storage[8] * -sinAngle;
    final double t2 = _m4storage[1] * cosAngle + _m4storage[9] * -sinAngle;
    final double t3 = _m4storage[2] * cosAngle + _m4storage[10] * -sinAngle;
    final double t4 = _m4storage[3] * cosAngle + _m4storage[11] * -sinAngle;
    final double t5 = _m4storage[0] * sinAngle + _m4storage[8] * cosAngle;
    final double t6 = _m4storage[1] * sinAngle + _m4storage[9] * cosAngle;
    final double t7 = _m4storage[2] * sinAngle + _m4storage[10] * cosAngle;
    final double t8 = _m4storage[3] * sinAngle + _m4storage[11] * cosAngle;
    _m4storage[0] = t1;
    _m4storage[1] = t2;
    _m4storage[2] = t3;
    _m4storage[3] = t4;
    _m4storage[8] = t5;
    _m4storage[9] = t6;
    _m4storage[10] = t7;
    _m4storage[11] = t8;
  }

  /**
   * Rotate this matrix [angle] radians around Z
   */
  public void rotateZ(double angle) {
    final double cosAngle = Math.cos(angle);
    final double sinAngle = Math.sin(angle);
    final double t1 = _m4storage[0] * cosAngle + _m4storage[4] * sinAngle;
    final double t2 = _m4storage[1] * cosAngle + _m4storage[5] * sinAngle;
    final double t3 = _m4storage[2] * cosAngle + _m4storage[6] * sinAngle;
    final double t4 = _m4storage[3] * cosAngle + _m4storage[7] * sinAngle;
    final double t5 = _m4storage[0] * -sinAngle + _m4storage[4] * cosAngle;
    final double t6 = _m4storage[1] * -sinAngle + _m4storage[5] * cosAngle;
    final double t7 = _m4storage[2] * -sinAngle + _m4storage[6] * cosAngle;
    final double t8 = _m4storage[3] * -sinAngle + _m4storage[7] * cosAngle;
    _m4storage[0] = t1;
    _m4storage[1] = t2;
    _m4storage[2] = t3;
    _m4storage[3] = t4;
    _m4storage[4] = t5;
    _m4storage[5] = t6;
    _m4storage[6] = t7;
    _m4storage[7] = t8;
  }

  /**
   * Scale this matrix by a [Vector3], [Vector4], or x,y,z
   */
  public void scale(double sx) {
    scale(sx, sx, sx);
  }

  public void scale(Vector3 v) {
    scale(v.getX(), v.getY(), v.getZ());
  }

  public void scale(Vector4 v) {
    scale(v.getX(), v.getY(), v.getZ(), v.getW());
  }

  public void scale(double sx, double sy, double sz) {
    scale(sx, sy, sz, 1.0);
  }

  public void scale(double sx, double sy, double sz, double sw) {
    _m4storage[0] *= sx;
    _m4storage[1] *= sx;
    _m4storage[2] *= sx;
    _m4storage[3] *= sx;
    _m4storage[4] *= sy;
    _m4storage[5] *= sy;
    _m4storage[6] *= sy;
    _m4storage[7] *= sy;
    _m4storage[8] *= sz;
    _m4storage[9] *= sz;
    _m4storage[10] *= sz;
    _m4storage[11] *= sz;
    _m4storage[12] *= sw;
    _m4storage[13] *= sw;
    _m4storage[14] *= sw;
    _m4storage[15] *= sw;
  }

  /**
   * Create a copy of [this] scaled by a [Vector3], [Vector4] or [x],[y], and
   * [z].
   */
  public Matrix4 scaled(double x) {
    return scaled(x, 1, 1);
  }

  public Matrix4 scaled(double x, double y) {
    return scaled(x, y, 1);
  }

  public Matrix4 scaled(double x, double y, double z) {
    final Matrix4 ret = clone();
    ret.scale(x, y, z);
    return ret;
  }

  /**
   * Zeros [this].
   */
  public void setZero() {
    _m4storage[0] = 0.0;
    _m4storage[1] = 0.0;
    _m4storage[2] = 0.0;
    _m4storage[3] = 0.0;
    _m4storage[4] = 0.0;
    _m4storage[5] = 0.0;
    _m4storage[6] = 0.0;
    _m4storage[7] = 0.0;
    _m4storage[8] = 0.0;
    _m4storage[9] = 0.0;
    _m4storage[10] = 0.0;
    _m4storage[11] = 0.0;
    _m4storage[12] = 0.0;
    _m4storage[13] = 0.0;
    _m4storage[14] = 0.0;
    _m4storage[15] = 0.0;
  }

  /**
   * Makes [this] into the identity matrix.
   */
  void setIdentity() {
    _m4storage[0] = 1.0;
    _m4storage[1] = 0.0;
    _m4storage[2] = 0.0;
    _m4storage[3] = 0.0;
    _m4storage[4] = 0.0;
    _m4storage[5] = 1.0;
    _m4storage[6] = 0.0;
    _m4storage[7] = 0.0;
    _m4storage[8] = 0.0;
    _m4storage[9] = 0.0;
    _m4storage[10] = 1.0;
    _m4storage[11] = 0.0;
    _m4storage[12] = 0.0;
    _m4storage[13] = 0.0;
    _m4storage[14] = 0.0;
    _m4storage[15] = 1.0;
  }

  /**
   * Returns the tranpose of this.
   */
  public Matrix4 transposed() {
    final Matrix4 ret = clone();
    ret.transpose();
    return ret;
  }

  public void transpose() {
    double temp;
    temp = _m4storage[4];
    _m4storage[4] = _m4storage[1];
    _m4storage[1] = temp;
    temp = _m4storage[8];
    _m4storage[8] = _m4storage[2];
    _m4storage[2] = temp;
    temp = _m4storage[12];
    _m4storage[12] = _m4storage[3];
    _m4storage[3] = temp;
    temp = _m4storage[9];
    _m4storage[9] = _m4storage[6];
    _m4storage[6] = temp;
    temp = _m4storage[13];
    _m4storage[13] = _m4storage[7];
    _m4storage[7] = temp;
    temp = _m4storage[14];
    _m4storage[14] = _m4storage[11];
    _m4storage[11] = temp;
  }

  /**
   * Returns the component wise absolute value of this.
   */
  public Matrix4 absolute() {
    final Matrix4 r = new Matrix4();
    final double[] rStorage = r._m4storage;
    rStorage[0] = Math.abs(_m4storage[0]);
    rStorage[1] = Math.abs(_m4storage[1]);
    rStorage[2] = Math.abs(_m4storage[2]);
    rStorage[3] = Math.abs(_m4storage[3]);
    rStorage[4] = Math.abs(_m4storage[4]);
    rStorage[5] = Math.abs(_m4storage[5]);
    rStorage[6] = Math.abs(_m4storage[6]);
    rStorage[7] = Math.abs(_m4storage[7]);
    rStorage[8] = Math.abs(_m4storage[8]);
    rStorage[9] = Math.abs(_m4storage[9]);
    rStorage[10] = Math.abs(_m4storage[10]);
    rStorage[11] = Math.abs(_m4storage[11]);
    rStorage[12] = Math.abs(_m4storage[12]);
    rStorage[13] = Math.abs(_m4storage[13]);
    rStorage[14] = Math.abs(_m4storage[14]);
    rStorage[15] = Math.abs(_m4storage[15]);
    return r;
  }

  /**
   * Returns the determinant of this matrix.
   */
  public double determinant() {
    final double det2_01_01 =
      _m4storage[0] * _m4storage[5] - _m4storage[1] * _m4storage[4];
    final double det2_01_02 =
      _m4storage[0] * _m4storage[6] - _m4storage[2] * _m4storage[4];
    final double det2_01_03 =
      _m4storage[0] * _m4storage[7] - _m4storage[3] * _m4storage[4];
    final double det2_01_12 =
      _m4storage[1] * _m4storage[6] - _m4storage[2] * _m4storage[5];
    final double det2_01_13 =
      _m4storage[1] * _m4storage[7] - _m4storage[3] * _m4storage[5];
    final double det2_01_23 =
      _m4storage[2] * _m4storage[7] - _m4storage[3] * _m4storage[6];
    final double det3_201_012 = _m4storage[8] * det2_01_12 -
                                _m4storage[9] * det2_01_02 +
                                _m4storage[10] * det2_01_01;
    final double det3_201_013 = _m4storage[8] * det2_01_13 -
                                _m4storage[9] * det2_01_03 +
                                _m4storage[11] * det2_01_01;
    final double det3_201_023 = _m4storage[8] * det2_01_23 -
                                _m4storage[10] * det2_01_03 +
                                _m4storage[11] * det2_01_02;
    final double det3_201_123 = _m4storage[9] * det2_01_23 -
                                _m4storage[10] * det2_01_13 +
                                _m4storage[11] * det2_01_12;
    return -det3_201_123 * _m4storage[12] +
           det3_201_023 * _m4storage[13] -
           det3_201_013 * _m4storage[14] +
           det3_201_012 * _m4storage[15];
  }

  /**
   * Returns the dot product of row [i] and [v].
   */
  public double dotRow(int i, Vector4 v) {
    final double[] vStorage = v._v4storage;
    return _m4storage[i] * vStorage[0] +
           _m4storage[4 + i] * vStorage[1] +
           _m4storage[8 + i] * vStorage[2] +
           _m4storage[12 + i] * vStorage[3];
  }

  /**
   * Returns the dot product of column [j] and [v].
   */
  public double dotColumn(int j, Vector4 v) {
    final double[] vStorage = v._v4storage;
    return _m4storage[j * 4] * vStorage[0] +
           _m4storage[j * 4 + 1] * vStorage[1] +
           _m4storage[j * 4 + 2] * vStorage[2] +
           _m4storage[j * 4 + 3] * vStorage[3];
  }

  /**
   * Returns the trace of the matrix. The trace of a matrix is the sum of the
   * diagonal entries.
   */
  public double trace() {
    double t = 0.0;
    t += _m4storage[0];
    t += _m4storage[5];
    t += _m4storage[10];
    t += _m4storage[15];
    return t;
  }

  /**
   * Returns infinity norm of the matrix. Used for numerical analysis.
   */
  public double infinityNorm() {
    double norm = 0.0;
    {
      double row_norm = 0.0;
      row_norm += Math.abs(_m4storage[0]);
      row_norm += Math.abs(_m4storage[1]);
      row_norm += Math.abs(_m4storage[2]);
      row_norm += Math.abs(_m4storage[3]);
      norm = Math.max(row_norm, norm);
    }
    {
      double row_norm = 0.0;
      row_norm += Math.abs(_m4storage[4]);
      row_norm += Math.abs(_m4storage[5]);
      row_norm += Math.abs(_m4storage[6]);
      row_norm += Math.abs(_m4storage[7]);
      norm = Math.max(row_norm, norm);
    }
    {
      double row_norm = 0.0;
      row_norm += Math.abs(_m4storage[8]);
      row_norm += Math.abs(_m4storage[9]);
      row_norm += Math.abs(_m4storage[10]);
      row_norm += Math.abs(_m4storage[11]);
      norm = Math.max(row_norm, norm);
    }
    {
      double row_norm = 0.0;
      row_norm += Math.abs(_m4storage[12]);
      row_norm += Math.abs(_m4storage[13]);
      row_norm += Math.abs(_m4storage[14]);
      row_norm += Math.abs(_m4storage[15]);
      norm = Math.max(row_norm, norm);
    }
    return norm;
  }

  /**
   * Returns relative error between [this] and [correct]
   */
  public double relativeError(Matrix4 correct) {
    final Matrix4 diff = correct.operatorSub(this);
    final double correct_norm = correct.infinityNorm();
    final double diff_norm = diff.infinityNorm();
    return diff_norm / correct_norm;
  }

  /**
   * Returns absolute error between [this] and [correct]
   */
  public double absoluteError(Matrix4 correct) {
    final double this_norm = infinityNorm();
    final double correct_norm = correct.infinityNorm();
    final double diff_norm = Math.abs(this_norm - correct_norm);
    return diff_norm;
  }

  /**
   * Returns the translation vector from this homogeneous transformation matrix.
   */
  public Vector3 getTranslation() {
    final double z = _m4storage[14];
    final double y = _m4storage[13];
    final double x = _m4storage[12];
    return new Vector3(x, y, z);
  }

  /**
   * Sets the translation vector in this homogeneous transformation matrix.
   */
  public void setTranslation(Vector3 t) {
    final double[] tStorage = t._v3storage;
    final double z = tStorage[2];
    final double y = tStorage[1];
    final double x = tStorage[0];
    _m4storage[14] = z;
    _m4storage[13] = y;
    _m4storage[12] = x;
  }

  /**
   * Sets the translation vector in this homogeneous transformation matrix.
   */
  public void setTranslationRaw(double x, double y, double z) {
    _m4storage[14] = z;
    _m4storage[13] = y;
    _m4storage[12] = x;
  }

  /**
   * Returns the max scale value of the 3 axes.
   */
  public double getMaxScaleOnAxis() {
    final double scaleXSq = _m4storage[0] * _m4storage[0] +
                            _m4storage[1] * _m4storage[1] +
                            _m4storage[2] * _m4storage[2];
    final double scaleYSq = _m4storage[4] * _m4storage[4] +
                            _m4storage[5] * _m4storage[5] +
                            _m4storage[6] * _m4storage[6];
    final double scaleZSq = _m4storage[8] * _m4storage[8] +
                            _m4storage[9] * _m4storage[9] +
                            _m4storage[10] * _m4storage[10];
    return Math.sqrt(Math.max(scaleXSq, Math.max(scaleYSq, scaleZSq)));
  }

  /**
   * Transposes just the upper 3x3 rotation matrix.
   */
  public void transposeRotation() {
    double temp;
    temp = _m4storage[1];
    _m4storage[1] = _m4storage[4];
    _m4storage[4] = temp;
    temp = _m4storage[2];
    _m4storage[2] = _m4storage[8];
    _m4storage[8] = temp;
    temp = _m4storage[4];
    _m4storage[4] = _m4storage[1];
    _m4storage[1] = temp;
    temp = _m4storage[6];
    _m4storage[6] = _m4storage[9];
    _m4storage[9] = temp;
    temp = _m4storage[8];
    _m4storage[8] = _m4storage[2];
    _m4storage[2] = temp;
    temp = _m4storage[9];
    _m4storage[9] = _m4storage[6];
    _m4storage[6] = temp;
  }

  /**
   * Invert [this].
   */
  public double invert() {
    return copyInverse(this);
  }

  /**
   * Set this matrix to be the inverse of [arg]
   */
  public double copyInverse(Matrix4 arg) {
    final double[] argStorage = arg._m4storage;
    final double a00 = argStorage[0];
    final double a01 = argStorage[1];
    final double a02 = argStorage[2];
    final double a03 = argStorage[3];
    final double a10 = argStorage[4];
    final double a11 = argStorage[5];
    final double a12 = argStorage[6];
    final double a13 = argStorage[7];
    final double a20 = argStorage[8];
    final double a21 = argStorage[9];
    final double a22 = argStorage[10];
    final double a23 = argStorage[11];
    final double a30 = argStorage[12];
    final double a31 = argStorage[13];
    final double a32 = argStorage[14];
    final double a33 = argStorage[15];
    final double b00 = a00 * a11 - a01 * a10;
    final double b01 = a00 * a12 - a02 * a10;
    final double b02 = a00 * a13 - a03 * a10;
    final double b03 = a01 * a12 - a02 * a11;
    final double b04 = a01 * a13 - a03 * a11;
    final double b05 = a02 * a13 - a03 * a12;
    final double b06 = a20 * a31 - a21 * a30;
    final double b07 = a20 * a32 - a22 * a30;
    final double b08 = a20 * a33 - a23 * a30;
    final double b09 = a21 * a32 - a22 * a31;
    final double b10 = a21 * a33 - a23 * a31;
    final double b11 = a22 * a33 - a23 * a32;
    final double det =
      (b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06);
    if (det == 0.0) {
      setFrom(arg);
      return 0.0;
    }
    final double invDet = 1.0 / det;
    _m4storage[0] = (a11 * b11 - a12 * b10 + a13 * b09) * invDet;
    _m4storage[1] = (-a01 * b11 + a02 * b10 - a03 * b09) * invDet;
    _m4storage[2] = (a31 * b05 - a32 * b04 + a33 * b03) * invDet;
    _m4storage[3] = (-a21 * b05 + a22 * b04 - a23 * b03) * invDet;
    _m4storage[4] = (-a10 * b11 + a12 * b08 - a13 * b07) * invDet;
    _m4storage[5] = (a00 * b11 - a02 * b08 + a03 * b07) * invDet;
    _m4storage[6] = (-a30 * b05 + a32 * b02 - a33 * b01) * invDet;
    _m4storage[7] = (a20 * b05 - a22 * b02 + a23 * b01) * invDet;
    _m4storage[8] = (a10 * b10 - a11 * b08 + a13 * b06) * invDet;
    _m4storage[9] = (-a00 * b10 + a01 * b08 - a03 * b06) * invDet;
    _m4storage[10] = (a30 * b04 - a31 * b02 + a33 * b00) * invDet;
    _m4storage[11] = (-a20 * b04 + a21 * b02 - a23 * b00) * invDet;
    _m4storage[12] = (-a10 * b09 + a11 * b07 - a12 * b06) * invDet;
    _m4storage[13] = (a00 * b09 - a01 * b07 + a02 * b06) * invDet;
    _m4storage[14] = (-a30 * b03 + a31 * b01 - a32 * b00) * invDet;
    _m4storage[15] = (a20 * b03 - a21 * b01 + a22 * b00) * invDet;
    return det;
  }

  public double invertRotation() {
    final double det = determinant();
    if (det == 0.0) {
      return 0.0;
    }
    final double invDet = 1.0 / det;
    final double ix;
    final double iy;
    final double iz;
    final double jx;
    final double jy;
    final double jz;
    final double kx;
    final double ky;
    final double kz;
    ix = invDet *
         (_m4storage[5] * _m4storage[10] - _m4storage[6] * _m4storage[9]);
    iy = invDet *
         (_m4storage[2] * _m4storage[9] - _m4storage[1] * _m4storage[10]);
    iz = invDet *
         (_m4storage[1] * _m4storage[6] - _m4storage[2] * _m4storage[5]);
    jx = invDet *
         (_m4storage[6] * _m4storage[8] - _m4storage[4] * _m4storage[10]);
    jy = invDet *
         (_m4storage[0] * _m4storage[10] - _m4storage[2] * _m4storage[8]);
    jz = invDet *
         (_m4storage[2] * _m4storage[4] - _m4storage[0] * _m4storage[6]);
    kx = invDet *
         (_m4storage[4] * _m4storage[9] - _m4storage[5] * _m4storage[8]);
    ky = invDet *
         (_m4storage[1] * _m4storage[8] - _m4storage[0] * _m4storage[9]);
    kz = invDet *
         (_m4storage[0] * _m4storage[5] - _m4storage[1] * _m4storage[4]);
    _m4storage[0] = ix;
    _m4storage[1] = iy;
    _m4storage[2] = iz;
    _m4storage[4] = jx;
    _m4storage[5] = jy;
    _m4storage[6] = jz;
    _m4storage[8] = kx;
    _m4storage[9] = ky;
    _m4storage[10] = kz;
    return det;
  }

  /**
   * Sets the upper 3x3 to a rotation of [radians] around X
   */
  public void setRotationX(double radians) {
    final double c = Math.cos(radians);
    final double s = Math.sin(radians);
    _m4storage[0] = 1.0;
    _m4storage[1] = 0.0;
    _m4storage[2] = 0.0;
    _m4storage[4] = 0.0;
    _m4storage[5] = c;
    _m4storage[6] = s;
    _m4storage[8] = 0.0;
    _m4storage[9] = -s;
    _m4storage[10] = c;
    _m4storage[3] = 0.0;
    _m4storage[7] = 0.0;
    _m4storage[11] = 0.0;
  }

  /**
   * Sets the upper 3x3 to a rotation of [radians] around Y
   */
  public void setRotationY(double radians) {
    final double c = Math.cos(radians);
    final double s = Math.sin(radians);
    _m4storage[0] = c;
    _m4storage[1] = 0.0;
    _m4storage[2] = -s;
    _m4storage[4] = 0.0;
    _m4storage[5] = 1.0;
    _m4storage[6] = 0.0;
    _m4storage[8] = s;
    _m4storage[9] = 0.0;
    _m4storage[10] = c;
    _m4storage[3] = 0.0;
    _m4storage[7] = 0.0;
    _m4storage[11] = 0.0;
  }

  /**
   * Sets the upper 3x3 to a rotation of [radians] around Z
   */
  public void setRotationZ(double radians) {
    final double c = Math.cos(radians);
    final double s = Math.sin(radians);
    _m4storage[0] = c;
    _m4storage[1] = s;
    _m4storage[2] = 0.0;
    _m4storage[4] = -s;
    _m4storage[5] = c;
    _m4storage[6] = 0.0;
    _m4storage[8] = 0.0;
    _m4storage[9] = 0.0;
    _m4storage[10] = 1.0;
    _m4storage[3] = 0.0;
    _m4storage[7] = 0.0;
    _m4storage[11] = 0.0;
  }

  /**
   * Converts into Adjugate matrix and scales by [scale]
   */
  public void scaleAdjoint(double scale) {
    // Adapted from code by Richard Carling.
    final double a1 = _m4storage[0];
    final double b1 = _m4storage[4];
    final double c1 = _m4storage[8];
    final double d1 = _m4storage[12];
    final double a2 = _m4storage[1];
    final double b2 = _m4storage[5];
    final double c2 = _m4storage[9];
    final double d2 = _m4storage[13];
    final double a3 = _m4storage[2];
    final double b3 = _m4storage[6];
    final double c3 = _m4storage[10];
    final double d3 = _m4storage[14];
    final double a4 = _m4storage[3];
    final double b4 = _m4storage[7];
    final double c4 = _m4storage[11];
    final double d4 = _m4storage[15];
    _m4storage[0] = (b2 * (c3 * d4 - c4 * d3) -
                     c2 * (b3 * d4 - b4 * d3) +
                     d2 * (b3 * c4 - b4 * c3)) *
                    scale;
    _m4storage[1] = -(a2 * (c3 * d4 - c4 * d3) -
                      c2 * (a3 * d4 - a4 * d3) +
                      d2 * (a3 * c4 - a4 * c3)) *
                    scale;
    _m4storage[2] = (a2 * (b3 * d4 - b4 * d3) -
                     b2 * (a3 * d4 - a4 * d3) +
                     d2 * (a3 * b4 - a4 * b3)) *
                    scale;
    _m4storage[3] = -(a2 * (b3 * c4 - b4 * c3) -
                      b2 * (a3 * c4 - a4 * c3) +
                      c2 * (a3 * b4 - a4 * b3)) *
                    scale;
    _m4storage[4] = -(b1 * (c3 * d4 - c4 * d3) -
                      c1 * (b3 * d4 - b4 * d3) +
                      d1 * (b3 * c4 - b4 * c3)) *
                    scale;
    _m4storage[5] = (a1 * (c3 * d4 - c4 * d3) -
                     c1 * (a3 * d4 - a4 * d3) +
                     d1 * (a3 * c4 - a4 * c3)) *
                    scale;
    _m4storage[6] = -(a1 * (b3 * d4 - b4 * d3) -
                      b1 * (a3 * d4 - a4 * d3) +
                      d1 * (a3 * b4 - a4 * b3)) *
                    scale;
    _m4storage[7] = (a1 * (b3 * c4 - b4 * c3) -
                     b1 * (a3 * c4 - a4 * c3) +
                     c1 * (a3 * b4 - a4 * b3)) *
                    scale;
    _m4storage[8] = (b1 * (c2 * d4 - c4 * d2) -
                     c1 * (b2 * d4 - b4 * d2) +
                     d1 * (b2 * c4 - b4 * c2)) *
                    scale;
    _m4storage[9] = -(a1 * (c2 * d4 - c4 * d2) -
                      c1 * (a2 * d4 - a4 * d2) +
                      d1 * (a2 * c4 - a4 * c2)) *
                    scale;
    _m4storage[10] = (a1 * (b2 * d4 - b4 * d2) -
                      b1 * (a2 * d4 - a4 * d2) +
                      d1 * (a2 * b4 - a4 * b2)) *
                     scale;
    _m4storage[11] = -(a1 * (b2 * c4 - b4 * c2) -
                       b1 * (a2 * c4 - a4 * c2) +
                       c1 * (a2 * b4 - a4 * b2)) *
                     scale;
    _m4storage[12] = -(b1 * (c2 * d3 - c3 * d2) -
                       c1 * (b2 * d3 - b3 * d2) +
                       d1 * (b2 * c3 - b3 * c2)) *
                     scale;
    _m4storage[13] = (a1 * (c2 * d3 - c3 * d2) -
                      c1 * (a2 * d3 - a3 * d2) +
                      d1 * (a2 * c3 - a3 * c2)) *
                     scale;
    _m4storage[14] = -(a1 * (b2 * d3 - b3 * d2) -
                       b1 * (a2 * d3 - a3 * d2) +
                       d1 * (a2 * b3 - a3 * b2)) *
                     scale;
    _m4storage[15] = (a1 * (b2 * c3 - b3 * c2) -
                      b1 * (a2 * c3 - a3 * c2) +
                      c1 * (a2 * b3 - a3 * b2)) *
                     scale;
  }

  /**
   * Rotates [arg] by the absolute rotation of [this]
   * Returns [arg].
   * Primarily used by AABB transformation code.
   */
  public Vector3 absoluteRotate(Vector3 arg) {
    final double m00 = Math.abs(_m4storage[0]);
    final double m01 = Math.abs(_m4storage[4]);
    final double m02 = Math.abs(_m4storage[8]);
    final double m10 = Math.abs(_m4storage[1]);
    final double m11 = Math.abs(_m4storage[5]);
    final double m12 = Math.abs(_m4storage[9]);
    final double m20 = Math.abs(_m4storage[2]);
    final double m21 = Math.abs(_m4storage[6]);
    final double m22 = Math.abs(_m4storage[10]);
    final double[] argStorage = arg._v3storage;
    final double x = argStorage[0];
    final double y = argStorage[1];
    final double z = argStorage[2];
    argStorage[0] = x * m00 + y * m01 + z * m02 + 0.0 * 0.0;
    argStorage[1] = x * m10 + y * m11 + z * m12 + 0.0 * 0.0;
    argStorage[2] = x * m20 + y * m21 + z * m22 + 0.0 * 0.0;
    return arg;
  }

  /**
   * Adds [o] to [this].
   */
  public void add(Matrix4 o) {
    final double[] oStorage = o._m4storage;
    _m4storage[0] = _m4storage[0] + oStorage[0];
    _m4storage[1] = _m4storage[1] + oStorage[1];
    _m4storage[2] = _m4storage[2] + oStorage[2];
    _m4storage[3] = _m4storage[3] + oStorage[3];
    _m4storage[4] = _m4storage[4] + oStorage[4];
    _m4storage[5] = _m4storage[5] + oStorage[5];
    _m4storage[6] = _m4storage[6] + oStorage[6];
    _m4storage[7] = _m4storage[7] + oStorage[7];
    _m4storage[8] = _m4storage[8] + oStorage[8];
    _m4storage[9] = _m4storage[9] + oStorage[9];
    _m4storage[10] = _m4storage[10] + oStorage[10];
    _m4storage[11] = _m4storage[11] + oStorage[11];
    _m4storage[12] = _m4storage[12] + oStorage[12];
    _m4storage[13] = _m4storage[13] + oStorage[13];
    _m4storage[14] = _m4storage[14] + oStorage[14];
    _m4storage[15] = _m4storage[15] + oStorage[15];
  }

  /**
   * Subtracts [o] from [this].
   */
  public void sub(Matrix4 o) {
    final double[] oStorage = o._m4storage;
    _m4storage[0] = _m4storage[0] - oStorage[0];
    _m4storage[1] = _m4storage[1] - oStorage[1];
    _m4storage[2] = _m4storage[2] - oStorage[2];
    _m4storage[3] = _m4storage[3] - oStorage[3];
    _m4storage[4] = _m4storage[4] - oStorage[4];
    _m4storage[5] = _m4storage[5] - oStorage[5];
    _m4storage[6] = _m4storage[6] - oStorage[6];
    _m4storage[7] = _m4storage[7] - oStorage[7];
    _m4storage[8] = _m4storage[8] - oStorage[8];
    _m4storage[9] = _m4storage[9] - oStorage[9];
    _m4storage[10] = _m4storage[10] - oStorage[10];
    _m4storage[11] = _m4storage[11] - oStorage[11];
    _m4storage[12] = _m4storage[12] - oStorage[12];
    _m4storage[13] = _m4storage[13] - oStorage[13];
    _m4storage[14] = _m4storage[14] - oStorage[14];
    _m4storage[15] = _m4storage[15] - oStorage[15];
  }

  /**
   * Negate [this].
   */
  public void negate() {
    _m4storage[0] = -_m4storage[0];
    _m4storage[1] = -_m4storage[1];
    _m4storage[2] = -_m4storage[2];
    _m4storage[3] = -_m4storage[3];
    _m4storage[4] = -_m4storage[4];
    _m4storage[5] = -_m4storage[5];
    _m4storage[6] = -_m4storage[6];
    _m4storage[7] = -_m4storage[7];
    _m4storage[8] = -_m4storage[8];
    _m4storage[9] = -_m4storage[9];
    _m4storage[10] = -_m4storage[10];
    _m4storage[11] = -_m4storage[11];
    _m4storage[12] = -_m4storage[12];
    _m4storage[13] = -_m4storage[13];
    _m4storage[14] = -_m4storage[14];
    _m4storage[15] = -_m4storage[15];
  }

  /**
   * Multiply [this] by [arg].
   */
  public void multiply(Matrix4 arg) {
    final double m00 = _m4storage[0];
    final double m01 = _m4storage[4];
    final double m02 = _m4storage[8];
    final double m03 = _m4storage[12];
    final double m10 = _m4storage[1];
    final double m11 = _m4storage[5];
    final double m12 = _m4storage[9];
    final double m13 = _m4storage[13];
    final double m20 = _m4storage[2];
    final double m21 = _m4storage[6];
    final double m22 = _m4storage[10];
    final double m23 = _m4storage[14];
    final double m30 = _m4storage[3];
    final double m31 = _m4storage[7];
    final double m32 = _m4storage[11];
    final double m33 = _m4storage[15];
    final double[] argStorage = arg._m4storage;
    final double n00 = argStorage[0];
    final double n01 = argStorage[4];
    final double n02 = argStorage[8];
    final double n03 = argStorage[12];
    final double n10 = argStorage[1];
    final double n11 = argStorage[5];
    final double n12 = argStorage[9];
    final double n13 = argStorage[13];
    final double n20 = argStorage[2];
    final double n21 = argStorage[6];
    final double n22 = argStorage[10];
    final double n23 = argStorage[14];
    final double n30 = argStorage[3];
    final double n31 = argStorage[7];
    final double n32 = argStorage[11];
    final double n33 = argStorage[15];
    _m4storage[0] = (m00 * n00) + (m01 * n10) + (m02 * n20) + (m03 * n30);
    _m4storage[4] = (m00 * n01) + (m01 * n11) + (m02 * n21) + (m03 * n31);
    _m4storage[8] = (m00 * n02) + (m01 * n12) + (m02 * n22) + (m03 * n32);
    _m4storage[12] = (m00 * n03) + (m01 * n13) + (m02 * n23) + (m03 * n33);
    _m4storage[1] = (m10 * n00) + (m11 * n10) + (m12 * n20) + (m13 * n30);
    _m4storage[5] = (m10 * n01) + (m11 * n11) + (m12 * n21) + (m13 * n31);
    _m4storage[9] = (m10 * n02) + (m11 * n12) + (m12 * n22) + (m13 * n32);
    _m4storage[13] = (m10 * n03) + (m11 * n13) + (m12 * n23) + (m13 * n33);
    _m4storage[2] = (m20 * n00) + (m21 * n10) + (m22 * n20) + (m23 * n30);
    _m4storage[6] = (m20 * n01) + (m21 * n11) + (m22 * n21) + (m23 * n31);
    _m4storage[10] = (m20 * n02) + (m21 * n12) + (m22 * n22) + (m23 * n32);
    _m4storage[14] = (m20 * n03) + (m21 * n13) + (m22 * n23) + (m23 * n33);
    _m4storage[3] = (m30 * n00) + (m31 * n10) + (m32 * n20) + (m33 * n30);
    _m4storage[7] = (m30 * n01) + (m31 * n11) + (m32 * n21) + (m33 * n31);
    _m4storage[11] = (m30 * n02) + (m31 * n12) + (m32 * n22) + (m33 * n32);
    _m4storage[15] = (m30 * n03) + (m31 * n13) + (m32 * n23) + (m33 * n33);
  }

  /**
   * Multiply a copy of [this] with [arg].
   */
  public Matrix4 multiplied(Matrix4 arg) {
    final Matrix4 ret = clone();
    ret.multiply(arg);
    return ret;
  }

  /**
   * Multiply a transposed [this] with [arg].
   */
  public void transposeMultiply(Matrix4 arg) {
    final double m00 = _m4storage[0];
    final double m01 = _m4storage[1];
    final double m02 = _m4storage[2];
    final double m03 = _m4storage[3];
    final double m10 = _m4storage[4];
    final double m11 = _m4storage[5];
    final double m12 = _m4storage[6];
    final double m13 = _m4storage[7];
    final double m20 = _m4storage[8];
    final double m21 = _m4storage[9];
    final double m22 = _m4storage[10];
    final double m23 = _m4storage[11];
    final double m30 = _m4storage[12];
    final double m31 = _m4storage[13];
    final double m32 = _m4storage[14];
    final double m33 = _m4storage[15];
    final double[] argStorage = arg._m4storage;
    _m4storage[0] = (m00 * argStorage[0]) +
                    (m01 * argStorage[1]) +
                    (m02 * argStorage[2]) +
                    (m03 * argStorage[3]);
    _m4storage[4] = (m00 * argStorage[4]) +
                    (m01 * argStorage[5]) +
                    (m02 * argStorage[6]) +
                    (m03 * argStorage[7]);
    _m4storage[8] = (m00 * argStorage[8]) +
                    (m01 * argStorage[9]) +
                    (m02 * argStorage[10]) +
                    (m03 * argStorage[11]);
    _m4storage[12] = (m00 * argStorage[12]) +
                     (m01 * argStorage[13]) +
                     (m02 * argStorage[14]) +
                     (m03 * argStorage[15]);
    _m4storage[1] = (m10 * argStorage[0]) +
                    (m11 * argStorage[1]) +
                    (m12 * argStorage[2]) +
                    (m13 * argStorage[3]);
    _m4storage[5] = (m10 * argStorage[4]) +
                    (m11 * argStorage[5]) +
                    (m12 * argStorage[6]) +
                    (m13 * argStorage[7]);
    _m4storage[9] = (m10 * argStorage[8]) +
                    (m11 * argStorage[9]) +
                    (m12 * argStorage[10]) +
                    (m13 * argStorage[11]);
    _m4storage[13] = (m10 * argStorage[12]) +
                     (m11 * argStorage[13]) +
                     (m12 * argStorage[14]) +
                     (m13 * argStorage[15]);
    _m4storage[2] = (m20 * argStorage[0]) +
                    (m21 * argStorage[1]) +
                    (m22 * argStorage[2]) +
                    (m23 * argStorage[3]);
    _m4storage[6] = (m20 * argStorage[4]) +
                    (m21 * argStorage[5]) +
                    (m22 * argStorage[6]) +
                    (m23 * argStorage[7]);
    _m4storage[10] = (m20 * argStorage[8]) +
                     (m21 * argStorage[9]) +
                     (m22 * argStorage[10]) +
                     (m23 * argStorage[11]);
    _m4storage[14] = (m20 * argStorage[12]) +
                     (m21 * argStorage[13]) +
                     (m22 * argStorage[14]) +
                     (m23 * argStorage[15]);
    _m4storage[3] = (m30 * argStorage[0]) +
                    (m31 * argStorage[1]) +
                    (m32 * argStorage[2]) +
                    (m33 * argStorage[3]);
    _m4storage[7] = (m30 * argStorage[4]) +
                    (m31 * argStorage[5]) +
                    (m32 * argStorage[6]) +
                    (m33 * argStorage[7]);
    _m4storage[11] = (m30 * argStorage[8]) +
                     (m31 * argStorage[9]) +
                     (m32 * argStorage[10]) +
                     (m33 * argStorage[11]);
    _m4storage[15] = (m30 * argStorage[12]) +
                     (m31 * argStorage[13]) +
                     (m32 * argStorage[14]) +
                     (m33 * argStorage[15]);
  }

  /**
   * Multiply [this] with a transposed [arg].
   */
  public void multiplyTranspose(Matrix4 arg) {
    final double m00 = _m4storage[0];
    final double m01 = _m4storage[4];
    final double m02 = _m4storage[8];
    final double m03 = _m4storage[12];
    final double m10 = _m4storage[1];
    final double m11 = _m4storage[5];
    final double m12 = _m4storage[9];
    final double m13 = _m4storage[13];
    final double m20 = _m4storage[2];
    final double m21 = _m4storage[6];
    final double m22 = _m4storage[10];
    final double m23 = _m4storage[14];
    final double m30 = _m4storage[3];
    final double m31 = _m4storage[7];
    final double m32 = _m4storage[11];
    final double m33 = _m4storage[15];
    final double[] argStorage = arg._m4storage;
    _m4storage[0] = (m00 * argStorage[0]) +
                    (m01 * argStorage[4]) +
                    (m02 * argStorage[8]) +
                    (m03 * argStorage[12]);
    _m4storage[4] = (m00 * argStorage[1]) +
                    (m01 * argStorage[5]) +
                    (m02 * argStorage[9]) +
                    (m03 * argStorage[13]);
    _m4storage[8] = (m00 * argStorage[2]) +
                    (m01 * argStorage[6]) +
                    (m02 * argStorage[10]) +
                    (m03 * argStorage[14]);
    _m4storage[12] = (m00 * argStorage[3]) +
                     (m01 * argStorage[7]) +
                     (m02 * argStorage[11]) +
                     (m03 * argStorage[15]);
    _m4storage[1] = (m10 * argStorage[0]) +
                    (m11 * argStorage[4]) +
                    (m12 * argStorage[8]) +
                    (m13 * argStorage[12]);
    _m4storage[5] = (m10 * argStorage[1]) +
                    (m11 * argStorage[5]) +
                    (m12 * argStorage[9]) +
                    (m13 * argStorage[13]);
    _m4storage[9] = (m10 * argStorage[2]) +
                    (m11 * argStorage[6]) +
                    (m12 * argStorage[10]) +
                    (m13 * argStorage[14]);
    _m4storage[13] = (m10 * argStorage[3]) +
                     (m11 * argStorage[7]) +
                     (m12 * argStorage[11]) +
                     (m13 * argStorage[15]);
    _m4storage[2] = (m20 * argStorage[0]) +
                    (m21 * argStorage[4]) +
                    (m22 * argStorage[8]) +
                    (m23 * argStorage[12]);
    _m4storage[6] = (m20 * argStorage[1]) +
                    (m21 * argStorage[5]) +
                    (m22 * argStorage[9]) +
                    (m23 * argStorage[13]);
    _m4storage[10] = (m20 * argStorage[2]) +
                     (m21 * argStorage[6]) +
                     (m22 * argStorage[10]) +
                     (m23 * argStorage[14]);
    _m4storage[14] = (m20 * argStorage[3]) +
                     (m21 * argStorage[7]) +
                     (m22 * argStorage[11]) +
                     (m23 * argStorage[15]);
    _m4storage[3] = (m30 * argStorage[0]) +
                    (m31 * argStorage[4]) +
                    (m32 * argStorage[8]) +
                    (m33 * argStorage[12]);
    _m4storage[7] = (m30 * argStorage[1]) +
                    (m31 * argStorage[5]) +
                    (m32 * argStorage[9]) +
                    (m33 * argStorage[13]);
    _m4storage[11] = (m30 * argStorage[2]) +
                     (m31 * argStorage[6]) +
                     (m32 * argStorage[10]) +
                     (m33 * argStorage[14]);
    _m4storage[15] = (m30 * argStorage[3]) +
                     (m31 * argStorage[7]) +
                     (m32 * argStorage[11]) +
                     (m33 * argStorage[15]);
  }

  // TODO(jacobr): this method might be worth pulling in the matrix3 class for.
  // Decomposes [this] into [translation], [rotation] and [scale] components.
  /*
  public void decompose(Vector3 translation, Quaternion rotation, Vector3 scale) {
    final Vector3 v = new Vector3();
    v.setValues(_m4storage[0], _m4storage[1], _m4storage[2]);
    double sx = v.getLength();
    v.setValues(_m4storage[4], _m4storage[5], _m4storage[6]);
    final double sy = v.getLength();
    v.setValues(_m4storage[8], _m4storage[9], _m4storage[10]);
    final double sz = v.getLength();

    if (determinant() < 0) {
      sx = -sx;
    }

    translation._v3storage[0] = _m4storage[12];
    translation._v3storage[1] = _m4storage[13];
    translation._v3storage[2] = _m4storage[14];

    final double invSX = 1.0 / sx;
    final double invSY = 1.0 / sy;
    final double invSZ = 1.0 / sz;

    final Matrix4 m = Matrix4.copy(this);
    m._m4storage[0] *= invSX;
    m._m4storage[1] *= invSX;
    m._m4storage[2] *= invSX;
    m._m4storage[4] *= invSY;
    m._m4storage[5] *= invSY;
    m._m4storage[6] *= invSY;
    m._m4storage[8] *= invSZ;
    m._m4storage[9] *= invSZ;
    m._m4storage[10] *= invSZ;

    rotation.setFromRotation(m.getRotation());

    scale._v3storage[0] = sx;
    scale._v3storage[1] = sy;
    scale._v3storage[2] = sz;
  }
*/

  /**
   * Rotate [arg] of type [Vector3] using the rotation defined by [this].
   */
  public Vector3 rotate3(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    final double x_ = (_m4storage[0] * argStorage[0]) +
                      (_m4storage[4] * argStorage[1]) +
                      (_m4storage[8] * argStorage[2]);
    final double y_ = (_m4storage[1] * argStorage[0]) +
                      (_m4storage[5] * argStorage[1]) +
                      (_m4storage[9] * argStorage[2]);
    final double z_ = (_m4storage[2] * argStorage[0]) +
                      (_m4storage[6] * argStorage[1]) +
                      (_m4storage[10] * argStorage[2]);
    argStorage[0] = x_;
    argStorage[1] = y_;
    argStorage[2] = z_;
    return arg;
  }

  /**
   * Rotate a copy of [arg] of type [Vector3] using the rotation defined by
   * [this]. If a [out] parameter is supplied, the copy is stored in [out].
   */
  public Vector3 rotated3(Vector3 arg, Vector3 out) {
    if (out == null) {
      out = Vector3.copy(arg);
    }
    else {
      out.setFrom(arg);
    }
    return rotate3(out);
  }

  /**
   * Transform [arg] of type [Vector3] using the transformation defined by
   * [this].
   */
  public Vector3 transform3(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    final double x_ = (_m4storage[0] * argStorage[0]) +
                      (_m4storage[4] * argStorage[1]) +
                      (_m4storage[8] * argStorage[2]) +
                      _m4storage[12];
    final double y_ = (_m4storage[1] * argStorage[0]) +
                      (_m4storage[5] * argStorage[1]) +
                      (_m4storage[9] * argStorage[2]) +
                      _m4storage[13];
    final double z_ = (_m4storage[2] * argStorage[0]) +
                      (_m4storage[6] * argStorage[1]) +
                      (_m4storage[10] * argStorage[2]) +
                      _m4storage[14];
    argStorage[0] = x_;
    argStorage[1] = y_;
    argStorage[2] = z_;
    return arg;
  }

  /**
   * Transform a copy of [arg] of type [Vector3] using the transformation
   * defined by [this]. If a [out] parameter is supplied, the copy is stored in
   * [out].
   */
  public Vector3 transformed3(Vector3 arg) {
    return transformed3(arg, null);
  }

  public Vector3 transformed3(Vector3 arg, Vector3 out) {
    if (out == null) {
      out = Vector3.copy(arg);
    }
    else {
      out.setFrom(arg);
    }
    return transform3(out);
  }

  /**
   * Transform [arg] of type [Vector4] using the transformation defined by
   * [this].
   */
  public Vector4 transform(Vector4 arg) {
    final double[] argStorage = arg._v4storage;
    final double x_ = (_m4storage[0] * argStorage[0]) +
                      (_m4storage[4] * argStorage[1]) +
                      (_m4storage[8] * argStorage[2]) +
                      (_m4storage[12] * argStorage[3]);
    final double y_ = (_m4storage[1] * argStorage[0]) +
                      (_m4storage[5] * argStorage[1]) +
                      (_m4storage[9] * argStorage[2]) +
                      (_m4storage[13] * argStorage[3]);
    final double z_ = (_m4storage[2] * argStorage[0]) +
                      (_m4storage[6] * argStorage[1]) +
                      (_m4storage[10] * argStorage[2]) +
                      (_m4storage[14] * argStorage[3]);
    final double w_ = (_m4storage[3] * argStorage[0]) +
                      (_m4storage[7] * argStorage[1]) +
                      (_m4storage[11] * argStorage[2]) +
                      (_m4storage[15] * argStorage[3]);
    argStorage[0] = x_;
    argStorage[1] = y_;
    argStorage[2] = z_;
    argStorage[3] = w_;
    return arg;
  }

  /**
   * Transform [arg] of type [Vector3] using the perspective transformation
   * defined by [this].
   */
  public Vector3 perspectiveTransform(Vector3 arg) {
    final double[] argStorage = arg._v3storage;
    final double x_ = (_m4storage[0] * argStorage[0]) +
                      (_m4storage[4] * argStorage[1]) +
                      (_m4storage[8] * argStorage[2]) +
                      _m4storage[12];
    final double y_ = (_m4storage[1] * argStorage[0]) +
                      (_m4storage[5] * argStorage[1]) +
                      (_m4storage[9] * argStorage[2]) +
                      _m4storage[13];
    final double z_ = (_m4storage[2] * argStorage[0]) +
                      (_m4storage[6] * argStorage[1]) +
                      (_m4storage[10] * argStorage[2]) +
                      _m4storage[14];
    final double w_ = 1.0 /
                      ((_m4storage[3] * argStorage[0]) +
                       (_m4storage[7] * argStorage[1]) +
                       (_m4storage[11] * argStorage[2]) +
                       _m4storage[15]);
    argStorage[0] = x_ * w_;
    argStorage[1] = y_ * w_;
    argStorage[2] = z_ * w_;
    return arg;
  }

  /**
   * Transform a copy of [arg] of type [Vector4] using the transformation
   * defined by [this]. If a [out] parameter is supplied, the copy is stored in
   * [out].
   */
  public Vector4 transformed(Vector4 arg) {
    return transformed(arg, null);
  }

  public Vector4 transformed(Vector4 arg, Vector4 out) {
    if (out == null) {
      out = Vector4.copy(arg);
    }
    else {
      out.setFrom(arg);
    }
    return transform(out);
  }

  /**
   * Copies [this] into [array] starting at [offset].
   */
  void copyIntoArray(double[] array, int offset /*= 0*/) {
    final int i = offset;
    array[i + 15] = _m4storage[15];
    array[i + 14] = _m4storage[14];
    array[i + 13] = _m4storage[13];
    array[i + 12] = _m4storage[12];
    array[i + 11] = _m4storage[11];
    array[i + 10] = _m4storage[10];
    array[i + 9] = _m4storage[9];
    array[i + 8] = _m4storage[8];
    array[i + 7] = _m4storage[7];
    array[i + 6] = _m4storage[6];
    array[i + 5] = _m4storage[5];
    array[i + 4] = _m4storage[4];
    array[i + 3] = _m4storage[3];
    array[i + 2] = _m4storage[2];
    array[i + 1] = _m4storage[1];
    array[i + 0] = _m4storage[0];
  }

  /**
   * Copies elements from [array] into [this] starting at [offset].
   */
  void copyFromArray(double[] array, int offset/* = 0*/) {
    final int i = offset;
    _m4storage[15] = array[i + 15];
    _m4storage[14] = array[i + 14];
    _m4storage[13] = array[i + 13];
    _m4storage[12] = array[i + 12];
    _m4storage[11] = array[i + 11];
    _m4storage[10] = array[i + 10];
    _m4storage[9] = array[i + 9];
    _m4storage[8] = array[i + 8];
    _m4storage[7] = array[i + 7];
    _m4storage[6] = array[i + 6];
    _m4storage[5] = array[i + 5];
    _m4storage[4] = array[i + 4];
    _m4storage[3] = array[i + 3];
    _m4storage[2] = array[i + 2];
    _m4storage[1] = array[i + 1];
    _m4storage[0] = array[i + 0];
  }

  /**
   * Multiply [this] to each set of xyz values in [array] starting at [offset].
   */
  double[] applyToVector3Array(double[] array) {
    return applyToVector3Array(array, 0);
  }

  double[] applyToVector3Array(double[] array, int offset) {
    for (int i = 0, j = offset; i < array.length; i += 3, j += 3) {
      final Vector3 v = Vector3.array(array, j);
      v.applyMatrix4(this);
      array[j] = v.getStorage()[0];
      array[j + 1] = v.getStorage()[1];
      array[j + 2] = v.getStorage()[2];
    }

    return array;
  }

  public Vector3 getRight() {
    final double x = _m4storage[0];
    final double y = _m4storage[1];
    final double z = _m4storage[2];
    return new Vector3(x, y, z);
  }

  public Vector3 getUp() {
    final double x = _m4storage[4];
    final double y = _m4storage[5];
    final double z = _m4storage[6];
    return new Vector3(x, y, z);
  }

  public Vector3 getForward() {
    final double x = _m4storage[8];
    final double y = _m4storage[9];
    final double z = _m4storage[10];
    return new Vector3(x, y, z);
  }

  /**
   * Is [this] the identity matrix?
   */
  public boolean isIdentity() {
    return _m4storage[0] == 1.0 // col 1
           &&
           _m4storage[1] == 0.0 &&
           _m4storage[2] == 0.0 &&
           _m4storage[3] == 0.0 &&
           _m4storage[4] == 0.0 // col 2
           &&
           _m4storage[5] == 1.0 &&
           _m4storage[6] == 0.0 &&
           _m4storage[7] == 0.0 &&
           _m4storage[8] == 0.0 // col 3
           &&
           _m4storage[9] == 0.0 &&
           _m4storage[10] == 1.0 &&
           _m4storage[11] == 0.0 &&
           _m4storage[12] == 0.0 // col 4
           &&
           _m4storage[13] == 0.0 &&
           _m4storage[14] == 0.0 &&
           _m4storage[15] == 1.0;
  }

  /**
   * Is [this] the zero matrix?
   */
  public boolean isZero() {
    return _m4storage[0] == 0.0 // col 1
           &&
           _m4storage[1] == 0.0 &&
           _m4storage[2] == 0.0 &&
           _m4storage[3] == 0.0 &&
           _m4storage[4] == 0.0 // col 2
           &&
           _m4storage[5] == 0.0 &&
           _m4storage[6] == 0.0 &&
           _m4storage[7] == 0.0 &&
           _m4storage[8] == 0.0 // col 3
           &&
           _m4storage[9] == 0.0 &&
           _m4storage[10] == 0.0 &&
           _m4storage[11] == 0.0 &&
           _m4storage[12] == 0.0 // col 4
           &&
           _m4storage[13] == 0.0 &&
           _m4storage[14] == 0.0 &&
           _m4storage[15] == 0.0;
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

/**
 * This code is ported from the Dart vector_math package.
 */
public class VectorUtil {
  public static double clamp(double x, double min, double max) {
    return Math.min(Math.max(x, min), max);
  }

  /**
   * 2D dot product.
   */
  public static double dot2(Vector2 x, Vector2 y) {
    return x.dot(y);
  }

  /**
   * 3D dot product.
   */
  public static double dot3(Vector3 x, Vector3 y) {
    return x.dot(y);
  }

  /**
   * 3D Cross product.
   */
  public static void cross3(Vector3 x, Vector3 y, Vector3 out) {
    x.crossInto(y, out);
  }

  /**
   * 2D cross product. vec2 x vec2.
   */
  public static double cross2(Vector2 x, Vector2 y) {
    return x.cross(y);
  }

  /**
   * 2D cross product. double x vec2.
   */
  public static void cross2A(double x, Vector2 y, Vector2 out) {
    final double tempy = x * y.getX();
    out.setX(-x * y.getY());
    out.setY(tempy);
  }

  /**
   * 2D cross product. vec2 x double.
   */
  public static void cross2B(Vector2 x, double y, Vector2 out) {
    final double tempy = -y * x.getX();
    out.setX(y * x.getY());
    out.setY(tempy);
  }
}


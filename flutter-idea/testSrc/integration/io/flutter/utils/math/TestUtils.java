/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * This code is ported from the Dart vector_math package.
 */
public class TestUtils {
  static final double errorThreshold = 0.0005;

  public static void relativeTest(Matrix4 output, Matrix4 expectedOutput) {
    for (int i = 0; i < 16; i++) {
      relativeTest(output.get(i), expectedOutput.get(i));
    }
  }

  public static void relativeTest(Vector4 output, Vector4 expectedOutput) {
    for (int i = 0; i < 4; i++) {
      relativeTest(output.getStorage()[i], expectedOutput.getStorage()[i]);
    }
  }

  public static void relativeTest(double output, double expectedOutput) {
    assertEquals(output, expectedOutput, errorThreshold);
  }

  public static void absoluteTest(Matrix4 output, Matrix4 expectedOutput) {
    for (int i = 0; i < 16; i++) {
      absoluteTest(output.get(i), expectedOutput.get(i));
    }
  }

  public static void absoluteTest(double output, double expectedOutput) {
    assertEquals(Math.abs(output), Math.abs(expectedOutput), errorThreshold);
  }

  public static Matrix4 parseMatrix4(String input) {
    input = input.trim();
    String[] rows = input.split("\n");
    ArrayList<Double> values = new ArrayList<Double>();
    int col_count = 0;
    for (int i = 0; i < rows.length; i++) {
      rows[i] = rows[i].trim();
      String[] cols = rows[i].split(" ");
      for (int j = 0; j < cols.length; j++) {
        cols[j] = cols[j].trim();
      }

      for (int j = 0; j < cols.length; j++) {
        if (cols[j].isEmpty()) {
          continue;
        }
        if (i == 0) {
          col_count++;
        }
        values.add(Double.parseDouble(cols[j]));
      }
    }

    Matrix4 m = new Matrix4();
    for (int j = 0; j < rows.length; j++) {
      for (int i = 0; i < col_count; i++) {
        m.set(m.index(j, i), values.get(j * col_count + i));
        //m[i][j] = values[j*col_count+i];
      }
    }

    return m;
  }
}

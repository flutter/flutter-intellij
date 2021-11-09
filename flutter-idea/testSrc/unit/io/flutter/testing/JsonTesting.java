/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

/**
 * Static methods useful in tests that work with JSON.
 */
public class JsonTesting {

  private JsonTesting() {}

  /**
   * Generates a JSON object expression.
   *
   * <p>Each pair should be in the form "key:value". Automatically quotes each key.
   * No escaping is done on the values.
   */
  public static String curly(String... pairs) {
    final StringBuilder out = new StringBuilder();
    out.append('{');
    boolean first = true;
    for (String pair : pairs) {
      final int colon = pair.indexOf(":");
      if (!first) out.append(",");
      out.append('"').append(pair.substring(0, colon)).append("\":").append(pair.substring(colon + 1));
      first = false;
    }
    out.append('}');
    return out.toString();
  }
}

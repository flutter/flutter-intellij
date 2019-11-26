/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import java.awt.*;

public class ExpressionParsingUtils {
  public static Integer parseNumberFromCallParam(String callText, String prefix) {
    if (callText.startsWith(prefix) && callText.endsWith(")")) {
      String val = callText.substring(prefix.length(), callText.length() - 1).trim();
      final int index = val.indexOf(',');
      if (index != -1) {
        val = val.substring(0, index);
      }
      try {
        return val.startsWith("0x")
               ? Integer.parseUnsignedInt(val.substring(2), 16)
               : Integer.parseUnsignedInt(val);
      }
      catch (NumberFormatException ignored) {
      }
    }

    return null;
  }

  public static Color parseColor(String text) {
    final Color color = parseColor(text, "const Color(");
    if (color != null) return color;
    return parseColor(text, "Color(");
  }

  public static Color parseColor(String text, String colorText) {
    final Integer val = parseNumberFromCallParam(text, colorText);
    if (val == null) return null;
    final int value = val;
    //noinspection UseJBColor
    return new Color((int)(value >> 16) & 0xFF, (int)(value >> 8) & 0xFF, (int)value & 0xFF, (int)(value >> 24) & 0xFF);
  }
}

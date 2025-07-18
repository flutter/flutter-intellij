/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ExpressionParsingUtils {
  public static @Nullable Integer parseNumberFromCallParam(String callText, String prefix) {
    if (prefix == null) {
      return null;
    }
    if (callText != null && callText.startsWith(prefix) && callText.endsWith(")")) {
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

  public static @Nullable Color parseColor(String text) {
    if (text == null) {
      return null;
    }
    final Color color = parseColor(text, "const Color(");
    if (color != null) return color;

    return parseColor(text, "Color(");
  }

  public static @Nullable Color parseColor(String text, String colorText) {
    if (text == null || colorText == null) {
      return null;
    }
    final Integer val = parseNumberFromCallParam(text, colorText);
    if (val == null) return null;

    try {
      final int value = val;
      //noinspection UseJBColor
      return new Color((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, (value >> 24) & 0xFF);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static @Nullable Color parseColorComponents(String callText, String prefix, boolean isARGB) {
    if (callText == null || prefix == null) {
      return null;
    }
    if (callText.startsWith(prefix) && callText.endsWith(")")) {
      final String colorString = callText.substring(prefix.length(), callText.length() - 1).trim();
      final String[] maybeNumbers = colorString.split(",");
      if (maybeNumbers.length < 4) {
        return null;
      }
      return isARGB ? parseARGBColorComponents(maybeNumbers) : parseRGBOColorComponents(maybeNumbers);
    }
    return null;
  }

  private static @Nullable Color parseARGBColorComponents(String[] maybeNumbers) {
    if (maybeNumbers == null || maybeNumbers.length < 4) {
      return null;
    }

    final int[] argb = new int[4];
    for (int i = 0; i < 4; ++i) {
      final String maybeNumber = maybeNumbers[i].trim();
      try {
        if (maybeNumber.startsWith("0x")) {
          argb[i] = Integer.parseUnsignedInt(maybeNumber.substring(2), 16);
        }
        else {
          argb[i] = Integer.parseUnsignedInt(maybeNumber);
        }
      }
      catch (NumberFormatException ignored) {
        return null;
      }
    }

    try {
      //noinspection UseJBColor
      return new Color(argb[1], argb[2], argb[3], argb[0]);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static @Nullable Color parseRGBOColorComponents(String[] maybeNumbers) {
    if (maybeNumbers == null) {
      return null;
    }
    final float[] rgbo = new float[4];
    for (int i = 0; i < 4; ++i) {
      final String maybeNumber = maybeNumbers[i].trim();
      try {
        if (i == 3) {
          rgbo[i] = Float.parseFloat(maybeNumber);
          if (rgbo[3] < 0.0f || rgbo[3] > 1.0f) {
            return null;
          }
        }
        else {
          if (maybeNumber.startsWith("0x")) {
            rgbo[i] = (float)Integer.parseUnsignedInt(maybeNumber.substring(2), 16) / 255;
          }
          else {
            rgbo[i] = (float)Integer.parseUnsignedInt(maybeNumber) / 255;
          }
          if (rgbo[i] < 0.0f || rgbo[i] > 1.0f) {
            return null;
          }
        }
      }
      catch (NumberFormatException ignored) {
        return null;
      }
    }

    //noinspection UseJBColor
    return new Color(rgbo[0], rgbo[1], rgbo[2], rgbo[3]);
  }
}

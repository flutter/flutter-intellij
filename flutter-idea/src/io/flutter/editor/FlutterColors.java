/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.diagnostic.Logger;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FlutterColors {
  private static final Logger LOG = Logger.getInstance(FlutterColors.class);

  public static class FlutterColor {
    @NotNull
    private final Color color;
    private final boolean isPrimary;

    FlutterColor(@NotNull Color color, boolean isPrimary) {
      this.color = color;
      this.isPrimary = isPrimary;
    }

    @NotNull
    public Color getAWTColor() {
      return color;
    }

    public boolean isPrimary() {
      return isPrimary;
    }
  }

  static final String primarySuffix = ".primary";
  static final String defaultShade = "[500]";

  private static final Properties colors;
  private static final Map<Color, String> colorToName;

  static {
    colors = new Properties();

    try {
      colors.load(FlutterUtils.class.getResourceAsStream("/flutter/colors/material.properties"));
    }
    catch (IOException e) {
      FlutterUtils.warn(LOG, e);
    }

    colorToName = new HashMap<>();
    for (Map.Entry<Object, Object> entry : colors.entrySet()) {
      final String name = (String)entry.getKey();
      final String value = (String)entry.getValue();
      final Color color = parseColor(value);
      if (color != null) {
        colorToName.put(color, name);
      }
    }
  }

  /**
   * @return the AWT color corresponding to the given Flutter color key.
   */
  @Nullable
  public static FlutterColor getColor(@NotNull String key) {
    // Handle things like Colors.blue.shade200; convert the text to blue[200].
    if (key.contains(".shade")) {
      key = key.replace(".shade", "[") + "]";
    }

    if (colors.containsKey(key)) {
      final Color color = getColorValue(key);
      if (color != null) {
        return new FlutterColor(color, false);
      }
    }
    else if (colors.containsKey(key + primarySuffix)) {
      final Color color = getColorValue(key + primarySuffix);
      if (color != null) {
        return new FlutterColor(color, true);
      }
    }

    return null;
  }

  /**
   * Returns the the shortest material color name matching a color if one exists.
   */
  @Nullable
  public static String getColorName(@Nullable Color color) {
    String name = colorToName.get(color);
    if (name == null) return null;
    // Normalize to avoid including suffixes that are not required.
    name = maybeTrimSuffix(name, primarySuffix);
    name = maybeTrimSuffix(name, defaultShade);
    return name;
  }

  private static String maybeTrimSuffix(String value, String suffix) {
    if (value.endsWith(suffix)) {
      return value.substring(0, value.length() - suffix.length());
    }
    return value;
  }

  private static Color parseColor(String hexValue) {
    if (hexValue == null) {
      return null;
    }

    try {
      // argb to r, g, b, a
      final long value = Long.parseLong(hexValue, 16);

      //noinspection UseJBColor
      return new Color((int)(value >> 16) & 0xFF, (int)(value >> 8) & 0xFF, (int)value & 0xFF, (int)(value >> 24) & 0xFF);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Color getColorValue(String name) {
    final String hexValue = colors.getProperty(name);
    return parseColor(hexValue);
  }
}

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
import java.util.Properties;

public class FlutterColors {

  private static final Logger LOG = Logger.getInstance(FlutterColors.class);

  public static class FlutterColor {
    @NotNull
    private final Color color;
    private final boolean isPrimary;

    private FlutterColor(@NotNull Color color, boolean isPrimary) {
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

  private static final Properties colors;

  static {
    colors = new Properties();

    try {
      colors.load(FlutterUtils.class.getResourceAsStream("/flutter/colors.properties"));
    }
    catch (IOException e) {
      LOG.warn(e);
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
    else if (colors.containsKey(key + ".primary")) {
      final Color color = getColorValue(key + ".primary");
      if (color != null) {
        return new FlutterColor(color, true);
      }
    }

    return null;
  }

  private static Color getColorValue(String name) {
    try {
      final String hexValue = colors.getProperty(name);
      if (hexValue == null) {
        return null;
      }

      // argb to r, g, b, a
      final long value = Long.parseLong(hexValue, 16);

      //noinspection UseJBColor
      return new Color((int)(value >> 16) & 0xFF, (int)(value >> 8) & 0xFF, (int)value & 0xFF, (int)(value >> 24) & 0xFF);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }
}

/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.diagnostic.Logger;
import io.flutter.FlutterUtils;
import io.flutter.logging.PluginLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FlutterCupertinoColors {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(FlutterCupertinoColors.class);

  private static final Properties colors;

  private static final Map<Color, String> colorToName;

  static {
    colors = new Properties();

    try {
      colors.load(FlutterUtils.class.getResourceAsStream("/flutter/colors/cupertino.properties"));
    }
    catch (IOException e) {
      FlutterUtils.warn(LOG, e);
    }

    colorToName = new HashMap<>();
    for (Map.Entry<Object, Object> entry : colors.entrySet()) {
      final String name = (String)entry.getKey();
      final String value = (String)entry.getValue();
      final Color color = FlutterColors.parseColor(value);
      if (color != null) {
        colorToName.put(color, name);
      }
    }
  }

  /**
   * @return the AWT color corresponding to the given Flutter color key.
   */
  @Nullable
  public static FlutterColors.FlutterColor getColor(@NotNull String key) {
    // Handle things like Colors.blue.shade200; convert the text to blue[200].
    if (key.contains(".shade")) {
      key = key.replace(".shade", "[") + "]";
    }

    if (colors.containsKey(key)) {
      final Color color = getColorValue(key);
      if (color != null) {
        return new FlutterColors.FlutterColor(color, false);
      }
    }
    else if (colors.containsKey(key + FlutterColors.primarySuffix)) {
      final Color color = getColorValue(key + FlutterColors.primarySuffix);
      if (color != null) {
        return new FlutterColors.FlutterColor(color, true);
      }
    }

    return null;
  }

  /**
   * Returns the shortest material color name matching a color if one exists.
   */
  @Nullable
  public static String getColorName(@Nullable Color color) {
    String name = colorToName.get(color);
    if (name == null) return null;
    // Normalize to avoid including suffixes that are not required.
    name = maybeTrimSuffix(name, FlutterColors.primarySuffix);
    name = maybeTrimSuffix(name, FlutterColors.defaultShade);
    return name;
  }

  private static String maybeTrimSuffix(String value, String suffix) {
    if (value.endsWith(suffix)) {
      return value.substring(0, value.length() - suffix.length());
    }
    return value;
  }

  private static @Nullable Color getColorValue(String name) {
    final String hexValue = colors.getProperty(name);
    return FlutterColors.parseColor(hexValue);
  }
}

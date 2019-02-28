/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import io.flutter.FlutterUtils;

import javax.swing.*;
import java.io.IOException;
import java.util.Properties;

public class FlutterMaterialIcons {
  private static final Logger LOG = Logger.getInstance(FlutterMaterialIcons.class);

  private static final Properties icons;

  static {
    icons = new Properties();

    try {
      icons.load(FlutterEditorAnnotator.class.getResourceAsStream("/flutter/material_icons.properties"));
    }
    catch (IOException e) {
      FlutterUtils.warn(LOG, e);
    }
  }

  public static Icon getIconForHex(String hexValue) {
    final String iconName = icons.getProperty(hexValue + ".codepoint");
    return getIcon(iconName);
  }

  public static Icon getIconForName(String name) {
    return getIcon(name);
  }

  private static Icon getIcon(String name) {
    if (name == null) {
      return null;
    }
    final String path = icons.getProperty(name);
    if (path == null) {
      return null;
    }
    return IconLoader.findIcon(path, FlutterMaterialIcons.class);
  }
}

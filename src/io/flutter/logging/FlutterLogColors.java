/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class FlutterLogColors {

  @NotNull
  static Color forCategory(@NotNull String category) {
    if (category.startsWith("flutter.")) {
      return JBColor.gray;
    }
    if (category.startsWith("runtime.")) {
      return JBColor.magenta;
    }
    if (category.equals("http")) {
      return JBColor.blue;
    }

    // TODO(pq): add more presets.

    // Default.
    return JBColor.darkGray;
  }
}

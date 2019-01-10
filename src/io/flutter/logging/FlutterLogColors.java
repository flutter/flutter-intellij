/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

public class FlutterLogColors {

  @NotNull
  public static Color forCategory(@NotNull String category) {
    if (category.startsWith("flutter.")) {
      return JBColor.gray;
    }
    if (category.startsWith("runtime.")) {
      return UIUtil.isUnderDarcula() ? JBColor.magenta : JBColor.pink;
    }
    if (category.equals("http")) {
      return JBColor.blue;
    }

    // TODO(pq): add more presets.

    // Default.
    return JBColor.darkGray;
  }
}

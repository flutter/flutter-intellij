/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class DevToolsUtils {
  public static String findWidgetId(String url) {
    final String searchFor = "inspectorRef=";
    final String[] split = url.split("&");
    for (String part : split) {
      if (part.startsWith(searchFor)) {
        return part.substring(searchFor.length());
      }
    }
    return null;
  }

  public String getColorHexCode() {
    return ColorUtil.toHex(UIUtil.getEditorPaneBackground());
  }

  public Boolean getIsBackgroundBright() {
    return JBColor.isBright();
  }

  public @NotNull Float getFontSize() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    if (manager == null) {
      // Return the default normal font size if editor manager is not found.
      return UIUtil.getFontSize(UIUtil.FontSize.NORMAL);
    }
    return (float) manager.getGlobalScheme().getEditorFontSize();
  }
}

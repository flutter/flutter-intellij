/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

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

  public Float getFontSize() {return UIUtil.getFontSize(UIUtil.FontSize.NORMAL);}
}

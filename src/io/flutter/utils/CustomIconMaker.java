/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

public class CustomIconMaker {
  private static final Color normalColor = ColorUtil.fromHex("231F20");

  private final Map<String, Icon> iconCache = new HashMap<>();

  public CustomIconMaker() {

  }

  public Icon getCustomIcon(String fromText) {
    return getCustomIcon(fromText, IconKind.kClass, false);
  }

  public Icon getCustomIcon(String fromText, IconKind kind) {
    return getCustomIcon(fromText, kind, false);
  }

  public Icon getCustomIcon(String fromText, IconKind kind, boolean isAbstract) {
    if (StringUtil.isEmpty(fromText)) {
      return null;
    }

    final String text = fromText.toUpperCase().substring(0, 1);
    final String mapKey = text + "_" + kind.name + "_" + isAbstract;

    if (!iconCache.containsKey(mapKey)) {
      final Icon baseIcon = isAbstract ? kind.abstractIcon : kind.icon;

      final Icon icon = new LayeredIcon(baseIcon, new Icon() {
        public void paintIcon(Component c, Graphics g, int x, int y) {
          final Graphics2D g2 = (Graphics2D)g.create();

          try {
            GraphicsUtil.setupAAPainting(g2);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(normalColor);

            final Font font = UIUtil.getFont(UIUtil.FontSize.MINI, UIUtil.getTreeFont());
            g2.setFont(font);

            final Rectangle2D bounds = g2.getFontMetrics().getStringBounds(text, g2);
            final float offsetX = (getIconWidth() - (float)bounds.getWidth()) / 2.0f;
            // Some black magic here for vertical centering.
            final float offsetY = getIconHeight() - ((getIconHeight() - (float)bounds.getHeight()) / 2.0f) - 2.0f;

            g2.drawString(text, x + offsetX, y + offsetY);
          }
          finally {
            g2.dispose();
          }
        }

        public int getIconWidth() {
          return baseIcon != null ? baseIcon.getIconWidth() : 13;
        }

        public int getIconHeight() {
          return baseIcon != null ? baseIcon.getIconHeight() : 13;
        }
      });

      iconCache.put(mapKey, icon);
    }

    return iconCache.get(mapKey);
  }

  public Icon fromWidgetName(String name) {
    if (name == null) {
      return null;
    }

    final boolean isPrivate = name.startsWith("_");
    while (!name.isEmpty() && !Character.isAlphabetic(name.charAt(0))) {
      name = name.substring(1);
    }

    if (name.isEmpty()) {
      return null;
    }

    return getCustomIcon(name, isPrivate ? CustomIconMaker.IconKind.kMethod : CustomIconMaker.IconKind.kClass);
  }

  public Icon fromInfo(String name) {
    if (name == null) {
      return null;
    }

    if (name.isEmpty()) {
      return null;
    }

    return getCustomIcon(name, CustomIconMaker.IconKind.kInfo);
  }

  public enum IconKind {
    kClass("class", FlutterIcons.CustomClass, FlutterIcons.CustomClassAbstract),
    kField("fields", FlutterIcons.CustomFields),
    kInterface("interface", FlutterIcons.CustomInterface),
    kMethod("method", FlutterIcons.CustomMethod, FlutterIcons.CustomMethodAbstract),
    kProperty("property", FlutterIcons.CustomProperty),
    kInfo("info", FlutterIcons.CustomInfo);

    public final String name;
    public final Icon icon;
    public final Icon abstractIcon;

    IconKind(String name, Icon icon) {
      this.name = name;
      this.icon = icon;
      this.abstractIcon = icon;
    }

    IconKind(String name, Icon icon, Icon abstractIcon) {
      this.name = name;
      this.icon = icon;
      this.abstractIcon = abstractIcon;
    }
  }
}

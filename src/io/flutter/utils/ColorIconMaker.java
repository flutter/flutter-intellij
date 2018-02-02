/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.util.ui.GraphicsUtil;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ColorIconMaker {
  private final Map<Color, Icon> iconCache = new HashMap<>();
  private static final int iconMargin = 2;

  public Icon getCustomIcon(Color color) {
    if (!iconCache.containsKey(color)) {
      final Icon icon = new Icon() {
        public void paintIcon(Component c, Graphics g, int x, int y) {
          final Graphics2D g2 = (Graphics2D)g.create();

          try {
            GraphicsUtil.setupAAPainting(g2);
            // draw a black and gray grid to use as the background to disambiguate
            // opaque colors from translucent colors.
            g2.setColor(Color.white);
            g2.fillRect(iconMargin, iconMargin, getIconWidth() - iconMargin * 2, getIconHeight() - iconMargin * 2);
            g2.setColor(Color.gray);
            g2.fillRect(iconMargin, iconMargin, getIconWidth() / 2 - iconMargin, getIconHeight() / 2 - iconMargin);
            g2.fillRect(getIconWidth() / 2, getIconHeight() / 2, getIconWidth() / 2 - iconMargin, getIconHeight() / 2 - iconMargin);
            g2.setColor(color);
            g2.fillRect(iconMargin, iconMargin, getIconWidth() - iconMargin * 2, getIconHeight() - iconMargin * 2);
            g2.setColor(Color.black);
            g2.drawRect(iconMargin, iconMargin, getIconWidth() - iconMargin * 2, getIconHeight() - iconMargin * 2);
          }
          finally {
            g2.dispose();
          }
        }

        public int getIconWidth() {
          return 22; // TODO(jacob): customize the icon height based on the font size.
        }

        public int getIconHeight() {
          return 22; // TODO(jacob): customize the icon height based on the font size.
        }
      };

      iconCache.put(color, icon);
    }

    return iconCache.get(color);
  }
}

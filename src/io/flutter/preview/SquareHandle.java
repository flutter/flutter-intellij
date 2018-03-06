/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * A small square handle approximately 7x7 pixels in size.
 */
public class SquareHandle extends Handle {
  private static final int SIZE = 7;

  private final SquareHandlePosition myPosition;

  public SquareHandle(SquareHandlePosition position) {
    myPosition = position;
  }

  @Override
  public void paint(Graphics g) {
    final int width = getWidth();
    final int height = getHeight();

    g.setColor(JBColor.BLACK);
    g.fillRect(0, 0, width, height);

    g.setColor(JBColor.WHITE);
    g.drawRect(0, 0, width, height);
  }

  @Override
  public void updateBounds(Rectangle host) {
    int x = 0, y = 0;
    switch (myPosition) {
      case TOP_LEFT:
        x = host.x;
        y = host.y;
        break;
      case TOP_RIGHT:
        x = host.x + host.width - 1;
        y = host.y;
        break;
      case BOTTOM_LEFT:
        x = host.x;
        y = host.y + host.height - 1;
        break;
      case BOTTOM_RIGHT:
        x = host.x + host.width - 1;
        y = host.y + host.height - 1;
        break;
    }
    setBounds(x - SIZE / 2, y - SIZE / 2, SIZE, SIZE);
  }
}

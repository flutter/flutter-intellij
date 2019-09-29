/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import java.awt.*;

/**
 * A handle for moving the host.
 */
public class MoveHandle extends Handle {
  @Override
  public void paint(Graphics g) {
    final Graphics2D g2 = (Graphics2D)g;

    //noinspection UseJBColor
    g2.setColor(Color.BLUE);

    final int width = getWidth();
    final int height = getHeight();
    g2.drawRect(0, 0, width - 1, height - 1);
    g2.drawRect(1, 1, width - 3, height - 3);
  }

  @Override
  public void updateBounds(Rectangle host) {
    setBounds(host);
  }
}

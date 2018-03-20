/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * A handle for moving the host.
 */
public class MoveHandle extends Handle {
  @Override
  public void paint(Graphics g) {
    final Graphics2D g2 = (Graphics2D)g;
    g2.setStroke(new BasicStroke(2));
    g2.setColor(JBColor.BLUE);
    g2.drawRect(0, 0, getWidth(), getHeight());
  }

  @Override
  public void updateBounds(Rectangle host) {
    setBounds(host);
  }
}

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
    g.drawRect(0, 0, getWidth(), getHeight());
  }

  @Override
  public void updateBounds(Rectangle host) {
    setBounds(host);
  }
}

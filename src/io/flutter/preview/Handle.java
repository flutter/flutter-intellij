/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;


import javax.swing.*;
import java.awt.*;

/**
 * A component that should track its host bounds.
 */
public abstract class Handle extends JComponent {
  /**
   * Update bounds of the handle using the bounds of its host.
   *
   * @param host the bounds of the host component in the handles layer.
   */
  public abstract void updateBounds(Rectangle host);
}

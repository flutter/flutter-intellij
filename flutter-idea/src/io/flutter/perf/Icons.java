/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import icons.FlutterIcons;
import io.flutter.utils.AnimatedIcon;

import javax.swing.*;
import java.awt.*;

public class Icons {
  static final AnimatedIcon RED_PROGRESS = new RedProgress();
  static final AnimatedIcon YELLOW_PROGRESS = new YellowProgress();
  static final AnimatedIcon NORMAL_PROGRESS = new AnimatedIcon.Grey();
  private static final Icon EMPTY_ICON = new EmptyIcon(FlutterIcons.CustomInfo);
  // Threshold for statistics to use red icons.
  static final int HIGH_LOAD_THRESHOLD = 2;

  public static Icon getIconForCount(int count, boolean showInactive) {
    if (count == 0) {
      return showInactive ? FlutterIcons.CustomInfo : EMPTY_ICON;
    }
    if (count >= HIGH_LOAD_THRESHOLD) {
      return YELLOW_PROGRESS;
    }
    return NORMAL_PROGRESS;
  }

  private static class EmptyIcon implements Icon {
    final Icon iconForSize;

    EmptyIcon(Icon iconForSize) {
      this.iconForSize = iconForSize;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
    }

    @Override
    public int getIconWidth() {
      return iconForSize.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return iconForSize.getIconHeight();
    }
  }

  // Spinning red progress icon
  //
  // TODO(jacobr): it would be nice to tint the icons programatically so that
  // we could have a wider range of icon colors representing various repaint
  // rates.
  static final class RedProgress extends AnimatedIcon {
    public RedProgress() {
      super(150,
            FlutterIcons.State.RedProgr_1,
            FlutterIcons.State.RedProgr_2,
            FlutterIcons.State.RedProgr_3,
            FlutterIcons.State.RedProgr_4,
            FlutterIcons.State.RedProgr_5,
            FlutterIcons.State.RedProgr_6,
            FlutterIcons.State.RedProgr_7,
            FlutterIcons.State.RedProgr_8);
    }
  }

  static final class YellowProgress extends AnimatedIcon {
    public YellowProgress() {
      super(150,
            FlutterIcons.State.YellowProgr_1,
            FlutterIcons.State.YellowProgr_2,
            FlutterIcons.State.YellowProgr_3,
            FlutterIcons.State.YellowProgr_4,
            FlutterIcons.State.YellowProgr_5,
            FlutterIcons.State.YellowProgr_6,
            FlutterIcons.State.YellowProgr_7,
            FlutterIcons.State.YellowProgr_8);
    }
  }
}

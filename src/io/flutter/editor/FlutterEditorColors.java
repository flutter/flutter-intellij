/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;

public class FlutterEditorColors {
  // XXX move these somewhere more standard.
  public final static Color TOOLTIP_BACKGROUND_COLOR = new Color(60, 60, 60, 230);
  public final static Color HIGHLIGHTED_RENDER_OBJECT_FILL_COLOR = new Color( 128, 128, 255, 128);
  public final static Color HIGHLIGHTED_RENDER_OBJECT_BORDER_COLOR = new Color(64, 64, 128, 128);

  public final static Stroke SOLID_STROKE = new BasicStroke(1);
  public final static JBColor VERY_LIGHT_GRAY = new JBColor(Gray._224, Gray._80);
  public final static JBColor SHADOW_GRAY = new JBColor(Gray._192, Gray._100);
  public final static JBColor OUTLINE_LINE_COLOR = new JBColor(Gray._128, Gray._128);
  public final static JBColor OUTLINE_LINE_COLOR_PAST_BLOCK = new JBColor(new Color(128, 128, 128, 65), new Color(128, 128, 128, 65));
  public final static JBColor BUILD_METHOD_STRIPE_COLOR = new JBColor(new Color(0xc0d8f0), new Color(0x8d7043));
}

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npwOld.platform;

import com.android.tools.idea.npwOld.FormFactor;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for dealing with Form Factors in Wizards.
 */
public class FormFactorUtils {
  /**
   * Create an image showing icons for each of the available form factors. The icons are drawn from left to right, using the form factors
   * large icon. This is an example of this method usage:
   * <pre>
   * <code>
   * myJLabel.setIcon(getFormFactorsImage(myJLabel, true));
   * </code>
   * </pre>
   * @param component Icon will be drawn in the context of the given {@code component}
   * @param requireEmulator If true, only include icons for form factors that have an emulator available.
   * @return <code>null</code> if it can't create a graphics Object to render the image (for example not enough memory)
   */
  @Nullable
  public static Icon getFormFactorsImage(JComponent component, boolean requireEmulator) {
    int width = 0;
    int height = 0;
    for (FormFactor formFactor : FormFactor.values()) {
      //if (formFactor == FormFactor.GLASS) {
      //  continue;
      //}
      if (requireEmulator && !formFactor.hasEmulator()) {
        continue;
      }
      Icon icon = formFactor.getLargeIcon();
      height = Math.max(height, icon.getIconHeight());
      width += formFactor.getLargeIcon().getIconWidth();
    }
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    int x = 0;
    for (FormFactor formFactor : FormFactor.values()) {
      if (requireEmulator && !formFactor.hasEmulator()) {
        continue;
      }
      //if (formFactor == FormFactor.GLASS) {
      //  continue;
      //}
      Icon icon = formFactor.getLargeIcon();
      icon.paintIcon(component, graphics, x, 0);
      x += icon.getIconWidth();
    }

    if (graphics != null) {
      graphics.dispose();
      return new ImageIcon(image);
    }
    else {
      return null;
    }
  }
}

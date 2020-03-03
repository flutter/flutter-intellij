/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.adtui.ImageComponent;
import com.intellij.ui.components.JBLabel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImagePreviewPanel {
  private JBLabel myImageLabel;
  private ImageComponent myImage;
  private JPanel myComponent;

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  public void setLabelText(@Nullable String title) {
    // The label is displayed on top of a gray/white checkered background, so we *do* want to use the actual Black color.
    //noinspection UseJBColor
    myImageLabel.setForeground(Color.BLACK);
    myImageLabel.setText(title);
  }

  public void setImage(@Nullable BufferedImage image) {
    if (image == null) {
      myImage.setIcon(null);
      return;
    }
    ImageIcon icon = new ImageIcon(image);
    Dimension d = new Dimension(icon.getIconWidth(), icon.getIconHeight());
    myImage.setPreferredSize(d);
    myImage.setMinimumSize(d);
    myImage.setIcon(icon);
  }

  public void setImageBorder(@Nullable Border border) {
    myImage.setBorder(border);
  }

  public void setImageBackground(@Nullable Color background) {
    myImage.setBackground(background);
  }

  public void setImageOpaque(boolean opaque) {
    myImage.setOpaque(opaque);
  }

  private void createUIComponents() {
    // Note: We override baseline so that the component can be vertically aligned at the bottom of the panel
    myComponent = new JPanel() {
      @Override
      public int getBaseline(int width, int height) {
        return height;
      }
    };
  }
}
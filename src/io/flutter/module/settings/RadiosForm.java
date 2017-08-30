/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemListener;

/**
 * A panel with two radios in a group.
 */
public class RadiosForm {
  private JRadioButton radio1;
  private JRadioButton radio2;
  private JPanel radiosPanel;
  private ButtonGroup radioGroup;

  public RadiosForm(String label1, String label2) {
    radio1.setText(label1);
    radio1.setSelected(true);

    radio2.setText(label2);

    radioGroup = new ButtonGroup();
    radioGroup.add(radio1);
    radioGroup.add(radio2);
  }

  @NotNull
  public ButtonGroup getGroup() {
    return radioGroup;
  }

  @NotNull
  public JComponent getComponent() {
    return radiosPanel;
  }

  public void setToolTipText(String text) {
    radiosPanel.setToolTipText(text);
  }

  public boolean isRadio2Selected() {
    return radio2.isSelected();
  }

  public void addItemListener(ItemListener listener) {
    radio1.addItemListener(listener);
  }
}

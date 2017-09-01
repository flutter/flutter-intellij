/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.EnumComboBoxModel;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterProjectType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemListener;

public class ProjectType {
  private JPanel projectTypePanel;
  private ComboBox projectTypeCombo;

  private void createUIComponents() {
    projectTypeCombo = new ComboBox<>();
    //noinspection unchecked
    projectTypeCombo.setModel(new EnumComboBoxModel<>(FlutterProjectType.class));
    projectTypeCombo.setToolTipText(FlutterBundle.message("flutter.module.create.settings.type.tip"));
  }

  @NotNull
  public JComponent getComponent() {
    return projectTypePanel;
  }

  public FlutterProjectType getType() {
    return (FlutterProjectType) projectTypeCombo.getSelectedItem();
  }

  public void addListener(ItemListener listener) {
    projectTypeCombo.addItemListener(listener);
  }
}

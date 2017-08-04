/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.openapi.ui.ComboBox;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ProjectType {
  private JPanel projectTypePanel;
  private ComboBox projectTypeCombo;

  private void createUIComponents() {
    projectTypeCombo = new ComboBox<>();
    //noinspection unchecked
    projectTypeCombo.setModel(new DefaultComboBoxModel(new String[]{
      FlutterBundle.message("flutter.module.create.settings.type.application"),
      FlutterBundle.message("flutter.module.create.settings.type.plugin")}));
    projectTypeCombo.setToolTipText(FlutterBundle.message("flutter.module.create.settings.type.tip"));
  }

  @NotNull
  public JComponent getComponent() {
    return projectTypePanel;
  }

  public boolean isPluginSelected() {
    return projectTypeCombo.getSelectedIndex() == 1;
  }
}

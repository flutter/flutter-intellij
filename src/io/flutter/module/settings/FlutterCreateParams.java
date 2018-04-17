/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterCreateParams {
  private JCheckBox createProjectOfflineCheckBox;
  private JPanel mainPanel;

  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }

  public boolean isOfflineSelected() {
    return createProjectOfflineCheckBox.isSelected();
  }

}

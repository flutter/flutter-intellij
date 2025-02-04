/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PackageDialogWrapper extends DialogWrapper {

  @NotNull
  private JTextField packageInput = new JTextField("", 16);

  @NotNull
  private JCheckBox devOnlyCheckBox = new JCheckBox();

  public PackageDialogWrapper() {
    super(true); // use current window as parent
    setTitle("Add Package");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel dialogPanel = new JPanel();

    JLabel packageNameLabel = new JLabel("Package name:");
    dialogPanel.add(packageNameLabel, BorderLayout.LINE_START);
    dialogPanel.add(packageInput, BorderLayout.CENTER);

    dialogPanel.add(new JSeparator());

    JLabel devOnlyLabel = new JLabel("Dev only:");
    dialogPanel.add(devOnlyLabel, BorderLayout.LINE_START);
    dialogPanel.add(devOnlyCheckBox, BorderLayout.CENTER);

    return dialogPanel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (getPackageName().isEmpty()) {
      return new ValidationInfo("A package name is required", packageInput);
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return packageInput;
  }

  @NotNull
  public String getPackageName() {
    String text = packageInput.getText();
    return text == null ? "" : text;
  }

  public boolean getDevOnly() {
    Object[] selectedObjects = devOnlyCheckBox.getSelectedObjects();
    if (selectedObjects == null) return false;
    return selectedObjects.length > 0;
  }
}

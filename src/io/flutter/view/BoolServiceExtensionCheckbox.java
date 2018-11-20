/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * This class performs the same role as FlutterViewToggleableAction but
 * renders the UI as a simple JCheckbox instead of as a DumbAwareAction
 * enabling embedding UI to turn on and off the service extension
 * in a JPanel.
 */
public class BoolServiceExtensionCheckbox {

  private final JCheckBox checkbox;

  BoolServiceExtensionCheckbox(FlutterApp app, @NotNull String extensionCommand, String label, String tooltip) {
    checkbox = new JCheckBox(label);
    checkbox.setHorizontalAlignment(JLabel.LEFT);
    checkbox.setToolTipText(tooltip);
    assert(app.getVMServiceManager() != null);
    app.hasServiceExtension(extensionCommand, checkbox::setEnabled);

    checkbox.addActionListener((l) -> {
      final boolean newValue = checkbox.isSelected();
      app.getVMServiceManager().setServiceExtensionState(
        extensionCommand,
        newValue,
        newValue);
      if (app.isSessionActive()) {
        app.callBooleanExtension(extensionCommand, newValue);
      }
    });

    app.getVMServiceManager().getServiceExtensionState(extensionCommand).listen((state) -> {
      if (checkbox.isSelected() != state.isEnabled()) {
        checkbox.setSelected(state.isEnabled());
      }
    }, true);
  }

  JCheckBox getComponent() {
    return checkbox;
  }
}

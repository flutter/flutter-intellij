/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBCheckBox;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StreamSubscription;
import io.flutter.vmService.ServiceExtensionState;
import io.flutter.vmService.ToggleableServiceExtensionDescription;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * This class performs the same role as FlutterViewToggleableAction but
 * renders the UI as a simple JCheckbox instead of as a DumbAwareAction
 * enabling embedding UI to turn on and off the service extension
 * in a JPanel.
 */
public class BoolServiceExtensionCheckbox implements Disposable {

  private final JCheckBox checkbox;
  private StreamSubscription<ServiceExtensionState> currentValueSubscription;

  BoolServiceExtensionCheckbox(
    FlutterApp app,
    @NotNull ToggleableServiceExtensionDescription extensionDescription,
    String tooltip) {
    checkbox = new JBCheckBox(extensionDescription.getDisabledText());
    checkbox.setHorizontalAlignment(JLabel.LEFT);
    checkbox.setToolTipText(tooltip);
    assert(app.getVMServiceManager() != null);
    app.hasServiceExtension(extensionDescription.getExtension(), checkbox::setEnabled, this);

    checkbox.addActionListener((l) -> {
      final boolean newValue = checkbox.isSelected();
      app.getVMServiceManager().setServiceExtensionState(
        extensionDescription.getExtension(),
        newValue,
        newValue);
      if (app.isSessionActive()) {
        app.callBooleanExtension(extensionDescription.getExtension(), newValue);
      }
    });

    currentValueSubscription = app.getVMServiceManager().getServiceExtensionState(extensionDescription.getExtension()).listen(
      (state) -> {
        if (checkbox.isSelected() != state.isEnabled()) {
          checkbox.setSelected(state.isEnabled());
        }
      }, true);
  }

  JCheckBox getComponent() {
    return checkbox;
  }

  @Override
  public void dispose() {
    if (currentValueSubscription != null) {
      currentValueSubscription.dispose();
      currentValueSubscription = null;
    }
  }
}

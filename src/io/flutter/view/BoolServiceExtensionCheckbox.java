/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * This class performs the same role as FlutterViewToggleableAction but
 * renders the UI as a simple JCheckbox instead of as a DumbAwareAction
 * enabling embedding UI to turn on and off the service extension
 * in a JPanel.
 */
public class BoolServiceExtensionCheckbox implements Disposable {

  private final EventStream<Boolean> currentValue;
  private final JCheckBox checkbox;
  private StreamSubscription<Boolean> currentValueSubscription;

  BoolServiceExtensionCheckbox(FlutterApp app, @NotNull String extensionCommand, String label, String tooltip) {
    checkbox = new JCheckBox(label);
    checkbox.setHorizontalAlignment(JLabel.LEFT);
    checkbox.setToolTipText(tooltip);
    assert(app.getVMServiceManager() != null);
    currentValue = app.getVMServiceManager().getServiceExtensionState(extensionCommand);
    app.hasServiceExtension(extensionCommand, checkbox::setEnabled, this);

    checkbox.addActionListener((l) -> {
      final boolean newValue = checkbox.isSelected();
      if (currentValue.setValue(newValue)) {
        if (app.isSessionActive()) {
          app.callBooleanExtension(extensionCommand, newValue);
        }
      }
    });

    currentValueSubscription = currentValue.listen((value) -> {
      if (checkbox.isSelected() != value) {
        checkbox.setSelected(value);
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

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.application.ApplicationManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StreamSubscription;
import io.flutter.vmService.ServiceExtensionState;
import io.flutter.vmService.ToggleableServiceExtensionDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class FlutterViewToggleableAction extends FlutterViewAction implements Toggleable, Disposable {
  private final ToggleableServiceExtensionDescription extensionDescription;
  private StreamSubscription<ServiceExtensionState> currentValueSubscription;

  FlutterViewToggleableAction(@NotNull FlutterApp app, @Nullable Icon icon, ToggleableServiceExtensionDescription extensionDescription) {
    // Assume the button is not enabled by default and pass disabledText here.
    super(app, extensionDescription.getDisabledText(), null, icon);
    this.extensionDescription = extensionDescription;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    // selected
    final boolean selected = this.isSelected();
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty("selected", selected);

    if (!app.isSessionActive()) {
      dispose();
      e.getPresentation().setEnabled(false);
      return;
    }

    if (currentValueSubscription == null) {
      currentValueSubscription =
        app.getVMServiceManager().getServiceExtensionState(extensionDescription.getExtension()).listen((state) -> {
          if (presentation.getClientProperty("selected") != (Boolean)state.isEnabled()) {
            presentation.putClientProperty("selected", state.isEnabled());
          }
        }, true);
    }

    presentation.setText(
      isSelected()
      ? extensionDescription.getEnabledText()
      : extensionDescription.getDisabledText());

    app.hasServiceExtension(extensionDescription.getExtension(), (enabled) -> {
      e.getPresentation().setEnabled(app.isSessionActive() && enabled);
    });
  }

  @Override
  public void dispose() {
    if (currentValueSubscription != null) {
      currentValueSubscription.dispose();
      currentValueSubscription = null;
    }
  }

  @Override
  protected void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension(extensionDescription.getExtension(), isSelected());
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    this.setSelected(event, !isSelected());
    super.actionPerformed(event);
  }

  public boolean isSelected() {
    return app.getVMServiceManager().getServiceExtensionState(extensionDescription.getExtension()).getValue().isEnabled();
  }

  public void setSelected(@Nullable AnActionEvent event, boolean selected) {
    app.getVMServiceManager().setServiceExtensionState(
      extensionDescription.getExtension(),
      selected,
      selected ? extensionDescription.getEnabledValue() : extensionDescription.getDisabledValue());

    if (event != null) {
      ApplicationManager.getApplication().invokeLater(() -> this.update(event));
    }
  }
}

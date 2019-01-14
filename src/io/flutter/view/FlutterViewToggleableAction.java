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
import io.flutter.server.vmService.ServiceExtensionState;
import io.flutter.utils.StreamSubscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class FlutterViewToggleableAction extends FlutterViewAction implements Toggleable, Disposable {
  private String extensionCommand;
  private Object enabledStateValue = true;
  private String enabledText;
  private String disabledText;
  private StreamSubscription<ServiceExtensionState> currentValueSubscription;

  FlutterViewToggleableAction(
    @NotNull FlutterApp app, @Nullable String text, @Nullable String enabledText, @Nullable String disabledText) {
    super(app, text);
    setActionTextValues(text, enabledText, disabledText);
  }

  FlutterViewToggleableAction(
    @NotNull FlutterApp app, @Nullable String text, @Nullable String enabledText, @Nullable String disabledText, @Nullable String description, @Nullable Icon icon) {
    super(app, text, description, icon);
    setActionTextValues(text, enabledText, disabledText);
  }

  private void setActionTextValues(String text, String enabledText, String disabledText) {
    this.enabledText = enabledText != null ? enabledText : text;
    this.disabledText = disabledText != null ? disabledText : text;
  }

  protected void setExtensionCommand(String extensionCommand) {
    this.extensionCommand = extensionCommand;
  }

  protected void setEnabledText(String enabledText) {
    this.enabledText = enabledText;
  }

  protected void setDisabledText(String disabledText) {
    this.disabledText = disabledText;
  }

  // Overrides the default enabledStateValue for Actions whose enabled value is not a boolean.
  protected void setEnabledStateValue(Object enabledStateValue) {
    this.enabledStateValue = enabledStateValue;
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
        app.getVMServiceManager().getServiceExtensionState(extensionCommand).listen((state) -> {
          if (presentation.getClientProperty("selected") != (Boolean) state.isEnabled()) {
            presentation.putClientProperty("selected", state.isEnabled());
          }
        }, true);
    }

    presentation.setText(isSelected() ? enabledText : disabledText);

    app.hasServiceExtension(extensionCommand, (enabled) -> {
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
      app.callBooleanExtension(extensionCommand, isSelected());
    }
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    this.setSelected(event, !isSelected());
    super.actionPerformed(event);
  }

  public boolean isSelected() {
    return app.getVMServiceManager().getServiceExtensionState(extensionCommand).getValue().isEnabled();
  }

  public void setSelected(@Nullable AnActionEvent event, boolean selected) {
    app.getVMServiceManager().setServiceExtensionState(
      extensionCommand,
      selected,
      selected ? enabledStateValue : null);

    if (event != null) {
      ApplicationManager.getApplication().invokeLater(() -> this.update(event));
    }
  }
}

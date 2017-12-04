/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import io.flutter.FlutterInitializer;
import io.flutter.run.daemon.FlutterDevice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class AbstractToggleableAction extends DumbAwareAction implements Toggleable {
  @NotNull final FlutterView view;
  private boolean selected = false;

  AbstractToggleableAction(@NotNull FlutterView view, @Nullable String text) {
    super(text);

    this.view = view;
  }

  AbstractToggleableAction(@NotNull FlutterView view, @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);

    this.view = view;
  }

  @Override
  public final void update(AnActionEvent e) {
    final boolean hasFlutterApp = view.getFlutterApp() != null;
    if (!hasFlutterApp) {
      selected = false;
    }

    // selected
    final boolean selected = this.isSelected(e);
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty("selected", selected);

    // enabled
    e.getPresentation().setEnabled(hasFlutterApp);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    if (view.getFlutterApp() == null) {
      return;
    }

    this.setSelected(event, !isSelected(event));
    final Presentation presentation = event.getPresentation();
    presentation.putClientProperty("selected", isSelected(event));

    FlutterInitializer.sendAnalyticsAction(this);
    perform(event);
  }

  protected abstract void perform(AnActionEvent event);

  public boolean isSelected(AnActionEvent var1) {
    return selected;
  }

  public void setSelected(AnActionEvent event, boolean selected) {
    this.selected = selected;

    ApplicationManager.getApplication().invokeLater(() -> this.update(event));
  }

  @Nullable
  protected FlutterDevice getDevice() {
    return view.getFlutterApp() == null ? null : view.getFlutterApp().device();
  }
}

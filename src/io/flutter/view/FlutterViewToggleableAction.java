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
import io.flutter.FlutterInitializer;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class FlutterViewToggleableAction extends FlutterViewAction implements Toggleable {
  private boolean selected = false;

  FlutterViewToggleableAction(@NotNull FlutterApp app, @Nullable String text) {
    super(app, text);
  }

  FlutterViewToggleableAction(@NotNull FlutterApp app, @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(app, text, description, icon);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    final boolean hasFlutterApp = app.isSessionActive();

    // selected
    final boolean selected = this.isSelected();
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty("selected", selected);

    // enabled
    e.getPresentation().setEnabled(hasFlutterApp);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    this.setSelected(event, !isSelected());
    final Presentation presentation = event.getPresentation();
    presentation.putClientProperty("selected", isSelected());

    FlutterInitializer.sendAnalyticsAction(this);
    perform(event);
  }

  protected abstract void perform(@Nullable AnActionEvent event);

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(@Nullable AnActionEvent event, boolean selected) {
    this.selected = selected;

    if (event != null) {
      ApplicationManager.getApplication().invokeLater(() -> this.update(event));
    }
  }
}

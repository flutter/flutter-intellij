/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
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
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class FlutterViewLocalToggleableAction extends FlutterViewAction implements Toggleable, Disposable {
  private boolean selected = false;
  private StreamSubscription<Boolean> subscription;
  private final EventStream<Boolean> stream;

  FlutterViewLocalToggleableAction(@NotNull FlutterApp app, @Nullable String text, EventStream<Boolean> stream) {
    super(app, text);
    this.stream = stream;
  }

  FlutterViewLocalToggleableAction(@NotNull FlutterApp app,
                                   @Nullable String text,
                                   @Nullable String description,
                                   @Nullable Icon icon,
                                   EventStream<Boolean> stream) {
    super(app, text, description, icon);
    this.stream = stream;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    // selected
    final boolean selected = this.isSelected();
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty("selected", selected);

    if (!app.isSessionActive()) {
      if (subscription != null) {
        subscription.dispose();
        subscription = null;
      }
      e.getPresentation().setEnabled(false);
      return;
    }

    if (subscription == null) {
      subscription = stream.listen((value) -> {
        this.setSelected(e, value);
      }, true);
    }
  }

  @Override
  public void dispose() {
    if (subscription != null) {
      subscription.dispose();
      subscription = null;
    }
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    this.setSelected(event, !isSelected());
    final Presentation presentation = event.getPresentation();
    presentation.putClientProperty("selected", isSelected());

    super.actionPerformed(event);
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    stream.setValue(selected);
  }

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

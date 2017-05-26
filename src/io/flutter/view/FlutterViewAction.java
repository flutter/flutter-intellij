/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class FlutterViewAction extends AnAction {
  @NotNull final FlutterView view;

  FlutterViewAction(@NotNull FlutterView view, @Nullable String text) {
    super(text);

    this.view = view;
  }

  FlutterViewAction(@NotNull FlutterView view, @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);

    this.view = view;
  }

  @Override
  public final void update(AnActionEvent e) {
    e.getPresentation().setEnabled(view.getFlutterApp() != null);
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UIUtils {

  @Nullable
  public static JComponent getComponentOfActionEvent(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    JComponent component = (JComponent)presentation.getClientProperty("button");
    if (component == null && e.getInputEvent().getSource() instanceof JComponent) {
      component = (JComponent)e.getInputEvent().getSource();
    }
    return component;
  }
}

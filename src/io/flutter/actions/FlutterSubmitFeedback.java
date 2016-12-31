/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

public class FlutterSubmitFeedback extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    FlutterInitializer.sendActionEvent(this);

    final String url = "https://github.com/flutter/flutter-intellij/issues/new";
    BrowserLauncher.getInstance().browse(url, null);
  }
}

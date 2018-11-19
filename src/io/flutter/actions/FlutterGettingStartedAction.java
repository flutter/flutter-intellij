/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

public class FlutterGettingStartedAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    FlutterInitializer.sendAnalyticsAction(this);
    BrowserLauncher.getInstance().browse(FlutterConstants.URL_GETTING_STARTED_IDE, null);
  }
}

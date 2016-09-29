/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;

public class RestartFlutterApp extends FlutterAppAction {

  public RestartFlutterApp(ObservatoryConnector connector, @NotNull Computable<Boolean> isApplicable) {
    super(connector, FlutterBundle.message("app.restart.action.text"), FlutterBundle.message("app.restart.action.description"),
          FlutterIcons.Flutter, isApplicable);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ifReadyThen(() -> {
      getApp().appRestart();
    });
  }
}

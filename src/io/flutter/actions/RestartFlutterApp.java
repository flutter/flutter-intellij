/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;

@SuppressWarnings("ComponentNotRegistered")
public class RestartFlutterApp extends FlutterAppAction {

  public static final String ID = "Flutter.RestartFlutterApp"; //NON-NLS

  public RestartFlutterApp(ObservatoryConnector connector, Computable<Boolean> isApplicable) {
    super(connector, FlutterBundle.message("app.restart.action.text"), FlutterBundle.message("app.restart.action.description"),
          FlutterIcons.Restart, isApplicable, ID);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ifReadyThen(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      getApp().performRestartApp();
    });
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;

@SuppressWarnings("ComponentNotRegistered")
public class RestartFlutterApp extends FlutterAppAction {
  public static final String ID = "Flutter.RestartFlutterApp"; //NON-NLS
  public static final String TEXT = FlutterBundle.message("app.restart.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.restart.action.description");

  public RestartFlutterApp(ObservatoryConnector connector, Computable<Boolean> isApplicable) {
    super(connector, TEXT, DESCRIPTION, FlutterIcons.Restart, isApplicable, ID);
    // Shortcut is associated with toolbar action.
    copyShortcutFrom(ActionManager.getInstance().getAction("Flutter.Toolbar.RestartAction"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FlutterInitializer.sendActionEvent(this);

    ifReadyThen(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      getApp().performRestartApp();
    });
  }
}

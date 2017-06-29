/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class RestartFlutterApp extends FlutterAppAction {
  public static final String ID = "Flutter.RestartFlutterApp"; //NON-NLS
  public static final String TEXT = FlutterBundle.message("app.restart.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.restart.action.description");

  public RestartFlutterApp(@NotNull FlutterApp app, @NotNull Computable<Boolean> isApplicable) {
    super(app, TEXT, DESCRIPTION, AllIcons.Actions.Restart, isApplicable, ID);
    // Shortcut is associated with toolbar action.
    copyShortcutFrom(ActionManager.getInstance().getAction("Flutter.Toolbar.RestartAction"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FlutterInitializer.sendAnalyticsAction(this);

    if (getApp().isStarted()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      getApp().performRestartApp();
    }
  }
}

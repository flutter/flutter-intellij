/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;

public class HotReloadFlutterApp extends FlutterAppAction {

  public HotReloadFlutterApp(ObservatoryConnector connector) {
    super(connector, FlutterBundle.message("app.reload.action.text"), FlutterBundle.message("app.reload.action.description"),
          FlutterIcons.SocialForward);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ifReadyThen(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      getApp().performHotReload();
    });
  }
}

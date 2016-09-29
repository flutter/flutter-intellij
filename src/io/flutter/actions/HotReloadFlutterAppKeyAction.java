/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import io.flutter.FlutterBundle;

/**
 * A keystroke invoked {@link HotReloadFlutterApp} action.
 */
public class HotReloadFlutterAppKeyAction extends FlutterKeyAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final ObservatoryConnector connector = findConnector();
    if (connector != null) {
      new HotReloadFlutterApp(connector, connector::isConnectionReady).actionPerformed(e);
    }
    else {
      Notifications.Bus.notify(
        new Notification(RELOAD_DISPLAY_ID,
                         FlutterBundle.message("no.flutter.app.title"),
                         FlutterBundle.message("no.flutter.app.description"),
                         NotificationType.WARNING));
    }
  }
}

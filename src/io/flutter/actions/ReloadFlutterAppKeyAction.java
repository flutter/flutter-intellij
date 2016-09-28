/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;

/**
 * A keystroke invoked {@link ReloadFlutterApp} action.
 */
public class ReloadFlutterAppKeyAction extends FlutterKeyAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ObservatoryConnector connector = findConnector();
    if (connector != null) {
      new ReloadFlutterApp(connector).actionPerformed(e);
    }
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract public class FlutterAppAction extends DumbAwareAction {

  private final ObservatoryConnector myConnector;

  public FlutterAppAction(ObservatoryConnector connector, String text, String description, Icon icon) {
    super(text, description, icon);
    myConnector = connector;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(myConnector.isConnectionReady());
  }

  boolean isConnectionReady() {
    return myConnector.isConnectionReady();
  }

  FlutterApp getApp() {
    return myConnector.getApp();
  }

  void ifReadyThen(Runnable x) {
    if (isConnectionReady()) {
      x.run();
    }
  }
}

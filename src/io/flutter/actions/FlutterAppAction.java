/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract public class FlutterAppAction extends DumbAwareAction {

  private final ObservatoryConnector myConnector;
  private Computable<Boolean> myIsApplicable;

  public FlutterAppAction(ObservatoryConnector connector, String text, String description, Icon icon, Computable<Boolean> isApplicable) {
    super(text, description, icon);
    myConnector = connector;
    myIsApplicable = isApplicable;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(myIsApplicable.compute());
  }

  FlutterApp getApp() {
    return myConnector.getApp();
  }

  void ifReadyThen(Runnable x) {
    if (myConnector.isConnectionReady()) {
      x.run();
    }
  }
}

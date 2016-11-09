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
  private FlutterApp.State myAppState;
  private boolean myIsListening = false;
  private FlutterApp.StateListener myListener = new FlutterApp.StateListener() {
    @Override
    public void stateChanged(FlutterApp.State newState) {
      myAppState = newState;
      getTemplatePresentation().setEnabled(myIsApplicable.compute() && isRunning());
    }
  };

  public FlutterAppAction(ObservatoryConnector connector, String text, String description, Icon icon, Computable<Boolean> isApplicable) {
    super(text, description, icon);
    myConnector = connector;
    myIsApplicable = isApplicable;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final boolean isConnected = myIsApplicable.compute();
    e.getPresentation().setEnabled(isConnected && isRunning());
    if (isConnected) {
      if (!myIsListening) {
        getApp().addStateListener(myListener);
        myIsListening = true;
      }
    }
    else {
      if (myIsListening) {
        getApp().removeStateListener(myListener);
        myIsListening = false;
      }
    }
  }

  FlutterApp getApp() {
    return myConnector.getApp();
  }

  void ifReadyThen(Runnable x) {
    if (myConnector.isConnectionReady() && isRunning()) {
      x.run();
    }
  }

  private boolean isRunning() {
    return myAppState == FlutterApp.State.STARTED;
  }
}

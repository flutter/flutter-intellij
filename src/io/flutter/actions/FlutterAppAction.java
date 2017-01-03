/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract public class FlutterAppAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(FlutterAppAction.class);

  private final ObservatoryConnector myConnector;
  private final Computable<Boolean> myIsApplicable;
  private FlutterApp.State myAppState;
  private final FlutterApp.StateListener myListener = new FlutterApp.StateListener() {
    @Override
    public void stateChanged(FlutterApp.State newState) {
      myAppState = newState;
      getTemplatePresentation().setEnabled(myIsApplicable.compute() && isRunning());
    }
  };
  private boolean myIsListening = false;

  public FlutterAppAction(ObservatoryConnector connector,
                          String text,
                          String description,
                          Icon icon,
                          Computable<Boolean> isApplicable,
                          @NotNull String actionId) {
    super(text, description, icon);
    myConnector = connector;
    myIsApplicable = isApplicable;
    registerAction(actionId);
  }

  private void registerAction(@NotNull String actionId) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(actionId);
    // New debug sessions create new actions, requiring us to overwrite existing ones in the registry.
    // TODO(pq): consider moving actions to our own registry for lookup.
    if (action != null) {
      actionManager.unregisterAction(actionId);
    }
    actionManager.registerAction(actionId, this);
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

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract public class FlutterAppAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(FlutterAppAction.class);

  @NotNull private final FlutterApp myApp;
  @NotNull private final Computable<Boolean> myIsApplicable;
  @NotNull private final String myActionId;

  private final FlutterApp.StateListener myListener = new FlutterApp.StateListener() {
    @Override
    public void stateChanged(FlutterApp.State newState) {
      getTemplatePresentation().setEnabled(myApp.isStarted() && myIsApplicable.compute());
    }
  };
  private boolean myIsListening = false;

  public FlutterAppAction(@NotNull FlutterApp app,
                          @NotNull String text,
                          @NotNull String description,
                          @NotNull Icon icon,
                          @NotNull Computable<Boolean> isApplicable,
                          @NotNull String actionId) {
    super(text, description, icon);

    myApp = app;
    myIsApplicable = isApplicable;
    myActionId = actionId;

    updateActionRegistration(app.isConnected());
  }

  private void updateActionRegistration(boolean isConnected) {
    final Project project = getApp().getProject();

    if (!isConnected) {
      // Unregister ourselves if we're the current action.
      if (ProjectActions.getAction(project, myActionId) == this) {
        ProjectActions.unregisterAction(project, myActionId);
      }
    }
    else {
      if (ProjectActions.getAction(project, myActionId) != this) {
        ProjectActions.registerAction(project, myActionId, this);
      }
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    updateActionRegistration(myApp.isConnected());

    final boolean isConnected = myIsApplicable.compute();
    e.getPresentation().setEnabled(myApp.isStarted() && isConnected);

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

  @NotNull
  public FlutterApp getApp() {
    return myApp;
  }
}

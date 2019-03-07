/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

abstract public class FlutterAppAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(FlutterAppAction.class);

  /**
   * The {@link FlutterApp} that this action specifically targets.
   */
  @NotNull private FlutterApp myApp;
  @NotNull private final Computable<Boolean> myIsApplicable;
  @NotNull private final String myActionId;

  /**
   * The set of all running {@link FlutterApp}s that this action could be applied to.
   *
   *
   */
  @NotNull private Set<FlutterApp> runningApps;

  private final FlutterApp.FlutterAppListener myListener = new FlutterApp.FlutterAppListener() {
    @Override
    public void stateChanged(FlutterApp.State newState) {
      getTemplatePresentation().setEnabled(runningApps.stream().anyMatch(FlutterApp::isStarted) && myIsApplicable.compute());
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
    runningApps = new HashSet<>();
    runningApps.add(myApp);

    updateActionRegistration(app.isConnected());
  }

  private void updateActionRegistration(boolean isConnected) {
    final Project project = getApp().getProject();

    final FlutterAppAction registeredAction = (FlutterAppAction)ProjectActions.getAction(project, myActionId);
    if (registeredAction != null) {
      runningApps.addAll(registeredAction.runningApps);
    }
    if (!isConnected) {
      // Unregister ourselves if we're the current action.
      if (registeredAction == this) {
        // if we preserve actions for all connected apps, then remove this app from the running apps list.
        runningApps.remove(myApp);
        Optional<FlutterApp> nextRunningApp = runningApps.stream().findFirst();
        if (!nextRunningApp.isPresent()) {
          ProjectActions.unregisterAction(project, myActionId);
        } else {
          myApp = nextRunningApp.get();
        }
      }
    }
    else {
      if (registeredAction != this) {
        // if we broadcast actions to all running apps, then join the app lists.
        if (registeredAction != null) {
          runningApps.addAll(registeredAction.runningApps);
        }
        ProjectActions.registerAction(project, myActionId, this);
      }
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    updateActionRegistration(myApp.isConnected());

    final boolean isConnected = myIsApplicable.compute();
    final boolean supportsReload = myApp.getMode().supportsReload();
    e.getPresentation().setEnabled(myApp.isStarted() && isConnected && supportsReload);

    if (isConnected) {
      if (!myIsListening) {
        for (FlutterApp app : runningApps) {
          app.addStateListener(myListener);
        }
        myIsListening = true;
      }
    }
    else {
      if (myIsListening) {
        for (FlutterApp app : runningApps) {
          app.removeStateListener(myListener);
        }
        myIsListening = false;
      }
    }
  }

  @NotNull
  public FlutterApp getApp() {
    return myApp;
  }

  @NotNull
  public Set<FlutterApp> getRunningApps() {
    return runningApps;
  }
}

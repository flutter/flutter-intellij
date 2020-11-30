/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.ObservatoryConnector;
import io.flutter.devtools.DevToolsManager;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class OpenDevToolsAction extends DumbAwareAction {
  private final @Nullable ObservatoryConnector myConnector;
  private final Computable<Boolean> myIsApplicable;
  private final FlutterApp myApp;

  public OpenDevToolsAction() {
    myApp = null;
    myConnector = null;
    myIsApplicable = null;
  }

  public OpenDevToolsAction(@NotNull final FlutterApp app, @NotNull final Computable<Boolean> isApplicable) {
    super("Open DevTools", "Open Dart DevTools", FlutterIcons.Dart_16);

    myApp = app;
    myConnector = app.getConnector();
    myIsApplicable = isApplicable;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    if (myIsApplicable == null) {
      e.getPresentation().setEnabled(true);
    }
    else {
      e.getPresentation().setEnabled(myIsApplicable.compute());
    }
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    FlutterInitializer.sendAnalyticsAction(this);

    Project project = event.getProject();
    if (project == null) {
      return;
    }

    final DevToolsManager devToolsManager = DevToolsManager.getInstance(project);

    if (myConnector == null) {
      if (devToolsManager.hasInstalledDevTools()) {
        devToolsManager.openBrowser(myApp);
      }
      else {
        final CompletableFuture<Boolean> result = devToolsManager.installDevTools();
        result.thenAccept(o -> devToolsManager.openBrowser(myApp));
      }
    }
    else {
      final String urlString = myConnector.getBrowserUrl();
      if (urlString == null) {
        return;
      }

      if (devToolsManager.hasInstalledDevTools()) {
        devToolsManager.openBrowserAndConnect(myApp, urlString);
      }
      else {
        final CompletableFuture<Boolean> result = devToolsManager.installDevTools();
        result.thenAccept(o -> devToolsManager.openBrowserAndConnect(myApp, urlString));
      }
    }
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class OpenDevToolsAction extends DumbAwareAction {
  private final @Nullable ObservatoryConnector myConnector;
  private final Computable<Boolean> myIsApplicable;

  public OpenDevToolsAction() {
    myConnector = null;
    myIsApplicable = null;
  }

  public OpenDevToolsAction(@NotNull final ObservatoryConnector connector, @NotNull final Computable<Boolean> isApplicable) {
    super("Open DevTools", "Open Dart DevTools", FlutterIcons.Dart_16);

    myConnector = connector;
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

    final Optional<FlutterApp> appOptional =
      FlutterApp.allFromProjectProcess(project).stream().filter((FlutterApp app) -> app.getProject() == project).findFirst();

    if (!appOptional.isPresent()) {
      Logger.getInstance(OpenDevToolsAction.class).error("DevTools cannot be opened because the app has been closed");
      return;
    }
    final FlutterApp app = appOptional.get();

    if (myConnector == null) {
      if (devToolsManager.hasInstalledDevTools()) {
        devToolsManager.openBrowser(app);
      }
      else {
        final CompletableFuture<Boolean> result = devToolsManager.installDevTools();
        result.thenAccept(o -> devToolsManager.openBrowser(app));
      }
    }
    else {
      final String urlString = myConnector.getBrowserUrl();
      if (urlString == null) {
        return;
      }

      if (devToolsManager.hasInstalledDevTools()) {
        devToolsManager.openBrowserAndConnect(app, urlString);
      }
      else {
        final CompletableFuture<Boolean> result = devToolsManager.installDevTools();
        result.thenAccept(o -> devToolsManager.openBrowserAndConnect(app, urlString));
      }
    }
  }
}

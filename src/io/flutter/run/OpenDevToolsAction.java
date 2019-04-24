/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.ObservatoryConnector;
import io.flutter.devtools.DevToolsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    if (event.getProject() == null) {
      return;
    }

    final DevToolsManager devToolsManager = DevToolsManager.getInstance(event.getProject());

    if (myConnector == null) {
      if (devToolsManager.hasInstalledDevTools()) {
        devToolsManager.openBrowser();
      }
      else {
        final CompletableFuture<Boolean> result = devToolsManager.installDevTools();
        result.thenAccept(o -> devToolsManager.openBrowser());
      }
    }
    else {
      final String urlString = myConnector.getBrowserUrl();
      if (urlString == null) {
        return;
      }

      if (devToolsManager.hasInstalledDevTools()) {
        devToolsManager.openBrowserAndConnect(urlString);
      }
      else {
        final CompletableFuture<Boolean> result = devToolsManager.installDevTools();
        result.thenAccept(o -> devToolsManager.openBrowserAndConnect(urlString));
      }
    }
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.ObservatoryConnector;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenDevToolsAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(OpenDevToolsAction.class);
  private static final String title = "Open Flutter DevTools";
  private final @Nullable ObservatoryConnector myConnector;
  private final Computable<Boolean> myIsApplicable;
  private final FlutterApp myApp;

  public OpenDevToolsAction() {
    myApp = null;
    myConnector = null;
    myIsApplicable = null;
  }

  public OpenDevToolsAction(@NotNull final FlutterApp app, @NotNull final Computable<Boolean> isApplicable) {
    super(title, title, FlutterIcons.Dart_16);

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


    AsyncUtils.whenCompleteUiThread(DevToolsService.getInstance(project).getDevToolsInstance(), (instance, ex) -> {
      if (project.isDisposed()) {
        return;
      }

      if (ex != null) {
        LOG.error(ex);
        return;
      }

      final String serviceUrl = myConnector != null && myConnector.getBrowserUrl() != null ? myConnector.getBrowserUrl() : null;

      BrowserLauncher.getInstance().browse(
        (new DevToolsUrl(instance.host, instance.port, serviceUrl, null, false, null, null).getUrlString()),
        null
      );
    });
  }
}

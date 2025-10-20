/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import icons.FlutterIcons;
import io.flutter.FlutterUtils;
import io.flutter.ObservatoryConnector;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.logging.PluginLogger;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class OpenDevToolsAction extends DumbAwareAction {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(OpenDevToolsAction.class);
  private static final String title = "Open Flutter DevTools in Browser";
  private @Nullable ObservatoryConnector myConnector;
  private final Computable<Boolean> myIsApplicable;

  public OpenDevToolsAction() {
    myConnector = null;
    myIsApplicable = null;
  }

  public OpenDevToolsAction(@NotNull final FlutterApp app, @NotNull final Computable<Boolean> isApplicable) {
    this(app.getConnector(), isApplicable);
  }

  public OpenDevToolsAction(@NotNull final ObservatoryConnector connector, @NotNull final Computable<Boolean> isApplicable) {
    super(title, title, FlutterIcons.Dart_16);

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
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }

    // This action is registered in plugin.xml with the default constructor.
    // Therefore, if a user triggers this from the IDE, even if there is a
    // running Flutter app myConnector will be null. In that case, check for a
    // Flutter app first and use its connector instead.
    // TODO(https://github.com/flutter/flutter-intellij/issues/8583): Open the
    // running app instead of the first one listed in the project processes.
    if (myConnector == null) {
      final List<FlutterApp> apps = FlutterApp.allFromProjectProcess(project);
      if (!apps.isEmpty()) {
        myConnector = apps.get(0).getConnector();
      }
    }

    AsyncUtils.whenCompleteUiThread(Objects.requireNonNull(DevToolsService.getInstance(project).getDevToolsInstance()), (instance, ex) -> {
      if (project.isDisposed()) {
        return;
      }

      if (ex != null) {
        FlutterUtils.error(LOG, "Exception in getDevToolsInstance", ex, true);
        return;
      }

      final String serviceUrl = myConnector != null && myConnector.getBrowserUrl() != null ? myConnector.getBrowserUrl() : null;

      FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
      assert instance != null;
      final String devToolsUrl = new DevToolsUrl.Builder().setDevToolsHost(instance.host())
        .setDevToolsPort(instance.port())
        .setVmServiceUri(serviceUrl)
        .setFlutterSdkVersion(flutterSdk == null ? null : flutterSdk.getVersion())
        .setWorkspaceCache(WorkspaceCache.getInstance(project))
        .setIdeFeature(DevToolsIdeFeature.RUN_CONSOLE)
        .build()
        .getUrlString();
      BrowserLauncher.getInstance().browse(devToolsUrl, null);
    });
  }
}

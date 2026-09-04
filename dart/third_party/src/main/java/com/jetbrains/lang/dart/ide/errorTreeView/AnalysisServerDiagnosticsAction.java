// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.errorTreeView;

import com.google.dart.server.GetServerPortConsumer;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.lsp.DartLspService;
import com.jetbrains.lang.dart.sdk.DartSdkUpdateChecker;

import org.dartlang.analysis.server.protocol.RequestError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnalysisServerDiagnosticsAction extends DumbAwareAction {
  private static final String MIN_LSP_DIAGNOSTIC_SERVER_SDK_VERSION = "3.13.0-106.0.dev";

  public AnalysisServerDiagnosticsAction() {
    super(DartBundle.messagePointer("analysis.server.show.diagnostics.text"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final DartAnalysisServerService service = project == null ? null : DartAnalysisServerService.getInstance(project);
    e.getPresentation().setEnabledAndVisible(service != null && service.isServerProcessActive());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    run(project, e);
  }

  void run(final @NotNull Project project, @Nullable AnActionEvent event) {
    String sdkVersion = DartAnalysisServerService.getInstance(project).getSdkVersion();
    if (!sdkVersion.isEmpty() &&
        DartSdkUpdateChecker.compareDartSdkVersions(sdkVersion, MIN_LSP_DIAGNOSTIC_SERVER_SDK_VERSION) >= 0) {
      useLspOverLegacy(project);
    } else {
      fallbackToLegacy(project);
    }

    if (event != null) {
      Analytics.report(AnalyticsData.forAction(this, event));
    } else {
      ActionManager actionManager = ActionManager.getInstance();
      if (actionManager != null) {
        Analytics.report(AnalyticsData.forAction(actionManager.getId(this), project));
      }
    }
  }

  private void useLspOverLegacy(@NotNull Project project) {
    DartLspService.getDiagnosticServerPort(project)
      .thenAccept(port -> {
        if (project.isDisposed()) return;
        if (port != null) {
          BrowserUtil.browse("http://localhost:" + port + "/status");
        } else {
          fallbackToLegacy(project);
        }
      })
      .exceptionally(ex -> {
        if (project.isDisposed()) return null;
        fallbackToLegacy(project);
        return null;
      });
  }

  private void fallbackToLegacy(@NotNull Project project) {
    DartAnalysisServerService server = DartAnalysisServerService.getInstance(project);
    server.diagnostic_getServerPort(new GetServerPortConsumer() {
      @Override
      public void computedServerPort(int port) {
        BrowserUtil.browse("http://localhost:" + port + "/status");
      }

      @Override
      public void onError(RequestError requestError) {
        String title = DartBundle.message("analysis.server.show.diagnostics.error");
        String message = requestError.getMessage();
        Notification notification = new Notification("Dart Analysis Server", title, message, NotificationType.ERROR);
        Notifications.Bus.notify(notification);
      }
    });
  }
}

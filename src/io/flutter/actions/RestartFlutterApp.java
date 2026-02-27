/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterMessages;
import io.flutter.analytics.AnalyticsConstants;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;

public class RestartFlutterApp extends FlutterAppAction {
  public static final String ID = "Flutter.RestartFlutterApp"; //NON-NLS
  public static final String TEXT = FlutterBundle.message("app.restart.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.restart.action.description");

  public RestartFlutterApp(@NotNull FlutterApp app, @NotNull Computable<Boolean> isApplicable) {
    super(app, TEXT, DESCRIPTION, FlutterIcons.HotRestart, isApplicable, ID);
    // Shortcut is associated with toolbar action.
    ActionManager actionManager = ActionManager.getInstance();
    if (actionManager != null) {
      var action = actionManager.getAction("Flutter.Toolbar.RestartAction");
      if (action != null) {
        copyShortcutFrom(action);
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }
    var reloadManager = FlutterReloadManager.getInstance(project);
    if (reloadManager != null) {
      reloadManager.saveAllAndRestart(getApp(), FlutterConstants.RELOAD_REASON_MANUAL);
    }

    var analyticsData = AnalyticsData.forAction(this, e);

    if (WorkspaceCache.getInstance(project).isBazel() &&
        FlutterSettings.getInstance().isShowBazelHotRestartWarning() &&
        !FlutterSettings.getInstance().isEnableBazelHotRestart()) {
      final Notification notification = new Notification(
        FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
        "Hot restart is not google3-specific",
        "Hot restart now disables google3-specific support by default. This makes hot restart faster and more robust, but hot " +
        "restart will not update generated files. To enable google3 hot restart, go to Settings > Flutter.",
        NotificationType.INFORMATION);
      Notifications.Bus.notify(notification, project);

      analyticsData.add(AnalyticsConstants.GOOGLE3, true);

      // We only want to show this notification once.
      FlutterSettings.getInstance().setShowBazelHotRestartWarning(false);
    }

    Analytics.report(analyticsData);
  }
}

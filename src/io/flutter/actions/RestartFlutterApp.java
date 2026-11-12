/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

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

    Analytics.report(createAnalyticsData(e.getPlace(), project));
  }

  /**
   * Builds the restart event. It uses {@link #ID} explicitly: this action is created per app and isn't
   * registered with the {@link ActionManager}, so {@code AnalyticsData.forAction(this, e)} would have a
   * null id and the event would be dropped.
   */
  @VisibleForTesting
  static @NotNull AnalyticsData createAnalyticsData(@NotNull String place, @Nullable Project project) {
    return AnalyticsData.forAction(ID, place, project);
  }
}

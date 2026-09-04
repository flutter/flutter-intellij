/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
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

import java.util.Objects;

/**
 * Action that reloads all running Flutter apps.
 */
public class ReloadAllFlutterApps extends FlutterAppAction {
  public static final String ID = "Flutter.ReloadAllFlutterApps"; //NON-NLS
  public static final String TEXT = FlutterBundle.message("app.reload.all.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.reload.all.action.description");

  public ReloadAllFlutterApps(@NotNull FlutterApp app, @NotNull Computable<Boolean> isApplicable) {
    super(app, TEXT, DESCRIPTION, FlutterIcons.HotReload, isApplicable, ID);
    // Shortcut is associated with toolbar action.
    copyShortcutFrom(
      Objects.requireNonNull(Objects.requireNonNull(ActionManager.getInstance()).getAction("Flutter.Toolbar.ReloadAllAction")));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }
    FlutterReloadManager reloadManager = FlutterReloadManager.getInstance(project);
    if (reloadManager != null) {
      reloadManager.saveAllAndReloadAll(FlutterApp.allFromProjectProcess(project), FlutterConstants.RELOAD_REASON_MANUAL);
    }

    Analytics.report(AnalyticsData.forAction(ID, e.getPlace(), e.getProject()));
  }
}

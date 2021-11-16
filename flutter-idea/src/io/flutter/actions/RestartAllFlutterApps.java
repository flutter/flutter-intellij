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
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

/**
 * Action that restarts all running Flutter apps.
 */
@SuppressWarnings("ComponentNotRegistered")
public class RestartAllFlutterApps extends FlutterAppAction {
  public static final String ID = "Flutter.RestartAllFlutterApps"; //NON-NLS
  public static final String TEXT = FlutterBundle.message("app.restart.all.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.restart.all.action.description");

  public RestartAllFlutterApps(@NotNull FlutterApp app, @NotNull Computable<Boolean> isApplicable) {
    super(app, TEXT, DESCRIPTION, FlutterIcons.HotRestart, isApplicable, ID);
    // Shortcut is associated with toolbar action.
    copyShortcutFrom(ActionManager.getInstance().getAction("Flutter.Toolbar.RestartAllAction"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }

    FlutterInitializer.sendAnalyticsAction(this);
    FlutterReloadManager.getInstance(project)
      .saveAllAndRestartAll(FlutterApp.allFromProjectProcess(project), FlutterConstants.RELOAD_REASON_MANUAL);
  }
}

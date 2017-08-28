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
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

@SuppressWarnings("ComponentNotRegistered")
public class ReloadFlutterApp extends FlutterAppAction {
  public static final String ID = "Flutter.ReloadFlutterApp"; //NON-NLS
  public static final String TEXT = FlutterBundle.message("app.reload.action.text");
  public static final String DESCRIPTION = FlutterBundle.message("app.reload.action.description");

  public ReloadFlutterApp(@NotNull FlutterApp app, @NotNull Computable<Boolean> isApplicable) {
    super(app, TEXT, DESCRIPTION, FlutterIcons.HotReload, isApplicable, ID);
    // Shortcut is associated with toolbar action.
    copyShortcutFrom(ActionManager.getInstance().getAction("Flutter.Toolbar.ReloadAction"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }

    // If the shift key is held down, perform a restart. We check to see if we're being invoked from the
    // 'GoToAction' dialog. If so, the modifiers are for the command that opened the go to action dialog.
    final boolean shouldRestart = (e.getModifiers() & InputEvent.SHIFT_MASK) != 0 && !"GoToAction".equals(e.getPlace());

    if (shouldRestart) {
      FlutterInitializer.sendAnalyticsAction(RestartFlutterApp.class.getSimpleName());
      FlutterReloadManager.getInstance(project).saveAllAndRestart(getApp());
    }
    else {
      // Else perform a hot reload.
      FlutterInitializer.sendAnalyticsAction(this);
      FlutterReloadManager.getInstance(project).saveAllAndReload(getApp());
    }
  }
}

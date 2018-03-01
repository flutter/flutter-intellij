/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import io.flutter.actions.OpenAndroidModule;
import org.jetbrains.annotations.NotNull;

public class FlutterStudioStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    // The IntelliJ version of this action spawns a new process for Android Studio.
    // Since we're already running Android Studio we want to simply open the project in the current process.
    replaceAction("flutter.androidstudio.open", new OpenAndroidModule());
  }

  public static void replaceAction(@NotNull String actionId, @NotNull AnAction newAction) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      newAction.getTemplatePresentation().setText(oldAction.getTemplatePresentation().getTextWithMnemonic(), true);
      newAction.getTemplatePresentation().setDescription(oldAction.getTemplatePresentation().getDescription());
      actionManager.unregisterAction(actionId);
    }
    actionManager.registerAction(actionId, newAction);
  }
}

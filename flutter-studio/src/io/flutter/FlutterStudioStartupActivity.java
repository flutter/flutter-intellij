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
import io.flutter.actions.FlutterShowStructureSettingsAction;
import io.flutter.actions.OpenAndroidModule;
import io.flutter.android.AndroidModuleLibraryManager;
import io.flutter.project.FlutterProjectCreator;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

public class FlutterStudioStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    if (!FlutterModuleUtils.hasFlutterModule(project)) {
      return;
    }
    // The IntelliJ version of this action spawns a new process for Android Studio.
    // Since we're already running Android Studio we want to simply open the project in the current process.
    replaceAction("flutter.androidstudio.open", new OpenAndroidModule());
    replaceAction("ShowProjectStructureSettings", new FlutterShowStructureSettingsAction());
    // Unset this flag for all projects, mainly to ease the upgrade from 3.0.1 to 3.1.
    // TODO(messick) Delete once 3.0.x has 0 7DA's.
    FlutterProjectCreator.disableUserConfig(project);
    // Monitor Android dependencies.
    if (FlutterSettings.getInstance().isSyncingAndroidLibraries() ||
        System.getProperty("flutter.android.library.sync", null) != null) {
      // TODO(messick): Remove the flag once this sync mechanism is stable.
      AndroidModuleLibraryManager.startWatching(project);
    }
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

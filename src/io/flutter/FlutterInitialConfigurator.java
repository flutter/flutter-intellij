/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.messages.MessageBus;
import io.flutter.actions.FlutterImportProjectAction;

public class FlutterInitialConfigurator {
  @SuppressWarnings("UnusedParameters")
  public FlutterInitialConfigurator(final MessageBus bus,
                                    final PropertiesComponent propertiesComponent,
                                    final FileTypeManager fileTypeManager) {
    // Intercept and redirect Import actions.
    if (!FlutterUtils.isAndroidStudio()) {
      // Replace the Welcome Screen Import action.
      final AnAction welcomeImportAction = ActionManager.getInstance().getAction("WelcomeScreen.ImportProject");
      if (welcomeImportAction != null) {
        FlutterUtils.replaceAction("WelcomeScreen.ImportProject",
                                   new FlutterImportProjectAction(welcomeImportAction, AllIcons.Welcome.ImportProject));
      }
      // Replace the IDE Import action.
      final AnAction importAction = ActionManager.getInstance().getAction("ImportProject");
      if (importAction != null) {
        FlutterUtils.replaceAction("ImportProject",
                                   new FlutterImportProjectAction(importAction));
      }
    }
  }
}

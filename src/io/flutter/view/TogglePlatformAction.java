/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.flutter.FlutterBundle;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

class TogglePlatformAction extends FlutterViewAction {
  private static final String PLATFORM_OVERRIDE = "ext.flutter.platformOverride";
  private Boolean isCurrentlyAndroid;
  CompletableFuture<Boolean> cachedHasExtensionFuture;

  TogglePlatformAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("flutter.view.togglePlatform.text"),
          FlutterBundle.message("flutter.view.togglePlatform.description"),
          AllIcons.RunConfigurations.Application);
  }

  @Override
  @SuppressWarnings("Duplicates")
  public void update(@NotNull AnActionEvent e) {
    if (!app.isSessionActive()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    app.hasServiceExtension(PLATFORM_OVERRIDE, (enabled) -> {
      e.getPresentation().setEnabled(app.isSessionActive() && enabled);
    });
  }

  @Override
  public void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      app.togglePlatform().thenAccept(isAndroid -> {
        if (isAndroid == null) {
          return;
        }

        app.togglePlatform(!isAndroid).thenAccept(isNowAndroid -> {
          if (app.getConsole() != null && isNowAndroid != null) {
            isCurrentlyAndroid = isNowAndroid;

            app.getConsole().print(
              FlutterBundle.message("flutter.view.togglePlatform.output",
                                    isNowAndroid ? "Android" : "iOS"),
              ConsoleViewContentType.SYSTEM_OUTPUT);

            app.getVMServiceManager().setServiceExtensionState(
              PLATFORM_OVERRIDE,
              true,
              isNowAndroid ? "android" : "iOS");
          }
        });
      });
    }
  }
}

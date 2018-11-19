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
import io.flutter.utils.StreamSubscription;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

class TogglePlatformAction extends FlutterViewAction {
  private Boolean isCurrentlyAndroid;
  CompletableFuture<Boolean> cachedHasExtensionFuture;
  private StreamSubscription<Boolean> subscription;

  TogglePlatformAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("flutter.view.togglePlatform.text"),
          FlutterBundle.message("flutter.view.togglePlatform.description"),
          AllIcons.RunConfigurations.Application);
  }

  @Override
  @SuppressWarnings("Duplicates")
  public void update(@NotNull AnActionEvent e) {
    if (!app.isSessionActive()) {
      if (subscription != null) {
        subscription.dispose();
        subscription = null;
      }
      e.getPresentation().setEnabled(false);
      return;
    }

    if (subscription == null) {
      subscription = app
        .hasServiceExtension("ext.flutter.platformOverride", (enabled) -> e.getPresentation().setEnabled(app.isSessionActive() && enabled));
    }
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
          }
        });
      });
    }
  }

  // TODO(kenzieschmoll): handle app restart in VMServiceManager so that this action synced across toolbars.
  @Override
  public void handleAppRestarted() {
    if (isCurrentlyAndroid != null) {
      app.togglePlatform(isCurrentlyAndroid);
    }
  }
}

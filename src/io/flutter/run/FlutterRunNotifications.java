/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.view.FlutterViewMessages;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class FlutterRunNotifications {
  public static final String GROUP_DISPLAY_ID = "Flutter App Run";

  private static final String RELOAD_ALREADY_RUN = "io.flutter.reload.alreadyRun";

  public static void init(@NotNull Project project) {
    // Initialize the flutter run notification group.
    NotificationsConfiguration.getNotificationsConfiguration().register(
      FlutterRunNotifications.GROUP_DISPLAY_ID,
      NotificationDisplayType.BALLOON,
      false);

    final FlutterRunNotifications notifications = new FlutterRunNotifications(project);

    //noinspection CodeBlock2Expr
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (event) -> {
        ApplicationManager.getApplication().invokeLater(notifications::checkForDisplayFirstReload);
      }
    );
  }

  @NotNull Project myProject;

  FlutterRunNotifications(@NotNull Project project) {
    this.myProject = project;
  }

  private void checkForDisplayFirstReload() {
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    final boolean alreadyRun = properties.getBoolean(RELOAD_ALREADY_RUN);
    if (!alreadyRun) {
      properties.setValue(RELOAD_ALREADY_RUN, true);

      final Notification notification = new Notification(
        GROUP_DISPLAY_ID,
        FlutterBundle.message("flutter.reload.firstRun.title"),
        FlutterBundle.message("flutter.reload.firstRun.content"),
        NotificationType.INFORMATION,
        (notification1, event) -> {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            if ("url".equals(event.getDescription())) {
              BrowserUtil.browse(FlutterBundle.message("flutter.reload.firstRun.url"));
            }
          }
        });
      notification.setIcon(FlutterIcons.Flutter);
      Notifications.Bus.notify(notification);
    }
  }
}

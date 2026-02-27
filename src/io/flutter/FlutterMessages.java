/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessagesService;
import icons.FlutterIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterMessages {
  public static final String FLUTTER_NOTIFICATION_GROUP_ID = "Flutter Messages";
  public static final String FLUTTER_LOGGING_NOTIFICATION_GROUP_ID = "Flutter Notifications";

  private FlutterMessages() {
  }

  public static void showError(@NotNull String title, @NotNull String message, @Nullable Project project) {
    Notifications.Bus.notify(
      new Notification(FLUTTER_NOTIFICATION_GROUP_ID,
                       title,
                       message,
                       NotificationType.ERROR), project);
  }

  public static void showWarning(@NotNull String title, @NotNull String message, @Nullable Project project) {
    Notifications.Bus.notify(
      new Notification(
        FLUTTER_NOTIFICATION_GROUP_ID,
        title,
        message,
        NotificationType.WARNING), project);
  }

  public static void showInfo(@NotNull String title, @NotNull String message, @Nullable Project project) {
    final Notification notification = new Notification(
      FLUTTER_NOTIFICATION_GROUP_ID,
      title,
      message,
      NotificationType.INFORMATION);
    notification.setIcon(FlutterIcons.Flutter);
    Notifications.Bus.notify(notification, project);
  }

  public static int showDialog(@Nullable Project project,
                               @NotNull String message,
                               @NotNull @Nls String title,
                               @NotNull String[] options,
                               int defaultOptionIndex) {
    return MessagesService.getInstance()
      .showMessageDialog(project, null, message, title,
                         options, defaultOptionIndex, -1,
                         FlutterIcons.Flutter_2x, null, true, null);
  }
}

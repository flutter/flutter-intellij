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
import com.intellij.openapi.ui.Messages;
import icons.FlutterIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterMessages {
  public static final String FLUTTER_NOTIFICATION_GOUP_ID = "Flutter Commands";

  private FlutterMessages() {
  }

  public static void showError(String title, String message) {
    Notifications.Bus.notify(
      new Notification(FLUTTER_NOTIFICATION_GOUP_ID,
                       title,
                       message,
                       NotificationType.ERROR));
  }

  public static int showDialog(@Nullable Project project,
                               @NotNull String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex) {
    return Messages.showIdeaMessageDialog(project, message, title,
                                          options, defaultOptionIndex,
                                          FlutterIcons.Flutter_2x, null);
  }
}

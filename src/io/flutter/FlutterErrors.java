/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;

public class FlutterErrors {
  public static final String FLUTTER_NOTIFICATION_GOUP_ID = "Flutter Commands";

  private FlutterErrors() {
  }

  public static void showError(String title, String message) {
    Notifications.Bus.notify(
      new Notification(FLUTTER_NOTIFICATION_GOUP_ID,
                       title,
                       message,
                       NotificationType.ERROR));
  }
}

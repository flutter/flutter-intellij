/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

// TODO(kenz): it would be nice to consolidate all notifications to use a single manager. Perhaps we should
// make `FlutterMessages` a private class in this file. Optionally, we could also move this functionality
// into `FlutterMessages`.
public class NotificationManager {
  private static final Set<String> shownNotifications = new HashSet<>();

  public static void showError(String title, String message, @Nullable String id, @Nullable Boolean showOnce) {
    if (shouldNotify(id, showOnce)) {
      shownNotifications.add(id);
      FlutterMessages.showError(title, message, null);
    }
  }

  public static void showWarning(String title, String message, @Nullable String id, @Nullable Boolean showOnce) {
    if (shouldNotify(id, showOnce)) {
      shownNotifications.add(id);
      FlutterMessages.showWarning(title, message, null);
    }
  }

  public static void showInfo(String title, String message, @Nullable String id, @Nullable Boolean showOnce) {
    if (shouldNotify(id, showOnce)) {
      shownNotifications.add(id);
      FlutterMessages.showInfo(title, message, null);
    }
  }

  private static boolean shouldNotify(@Nullable String id, @Nullable Boolean showOnce) {
    // This notification has already been shown and it can only be shown once.
    return id == null || !shownNotifications.contains(id) || showOnce == null || !showOnce;
  }

  public static void reset() {
    shownNotifications.clear();
  }
}

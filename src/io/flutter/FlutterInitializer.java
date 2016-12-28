/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import io.flutter.analytics.Analytics;
import io.flutter.run.daemon.FlutterDaemonService;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.UUID;

public class FlutterInitializer implements StartupActivity {
  private static final String analyticsClientIdKey = "io.flutter.analytics.clientId";
  private static final String analyticsOptOutKey = "io.flutter.analytics.optOut";
  private static final String analyticsToastShown = "io.flutter.analytics.toastShown";

  private static Analytics analytics;

  @NotNull
  public static Analytics getAnalytics() {
    if (analytics == null) {
      final PropertiesComponent properties = PropertiesComponent.getInstance();

      String clientId = properties.getValue(analyticsClientIdKey);
      if (clientId == null) {
        clientId = UUID.randomUUID().toString();
        properties.setValue(analyticsClientIdKey, clientId);
      }

      final IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId("io.flutter"));
      assert descriptor != null;
      analytics = new Analytics(clientId, descriptor.getVersion());

      // Set up reporting prefs.
      analytics.setCanSend(getCanReportAnalytics());

      // Send initial loading hit.
      analytics.sendScreenView("main");
    }

    return analytics;
  }

  public static void setCanReportAnalaytics(boolean canReportAnalaytics) {
    if (getCanReportAnalytics() != canReportAnalaytics) {
      final boolean wasReporting = getCanReportAnalytics();

      final PropertiesComponent properties = PropertiesComponent.getInstance();
      properties.setValue(analyticsOptOutKey, !canReportAnalaytics);
      if (analytics != null) {
        analytics.setCanSend(getCanReportAnalytics());
      }

      if (!wasReporting && canReportAnalaytics) {
        getAnalytics().sendScreenView("main");
      }
    }
  }

  public static boolean getCanReportAnalytics() {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    return !properties.getBoolean(analyticsOptOutKey, false);
  }

  public static void sendActionEvent(@NotNull AnAction action) {
    getAnalytics().sendEvent("intellij", action.getClass().getSimpleName());
  }

  @Override
  public void runActivity(@NotNull Project project) {
    // Initialize the daemon service (this starts a device watcher).
    FlutterDaemonService.getInstance(project);

    // Initialize analytics.
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (!properties.getBoolean(analyticsToastShown)) {
      properties.setValue(analyticsToastShown, true);

      final Notification notification = new Notification(
        FlutterErrors.FLUTTER_NOTIFICATION_GOUP_ID,
        FlutterBundle.message("flutter.analytics.notification.title"),
        FlutterBundle.message("flutter.analytics.notification.content"),
        NotificationType.INFORMATION,
        (notification1, event) -> {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            if ("url".equals(event.getDescription())) {
              BrowserUtil.browse("https://www.google.com/policies/privacy/");
            }
          }
        });
      notification.addAction(new AnAction(FlutterBundle.message("flutter.analytics.notification.accept")) {
        @Override
        public void actionPerformed(AnActionEvent event) {
          notification.expire();
          getAnalytics();
        }
      });
      notification.addAction(new AnAction(FlutterBundle.message("flutter.analytics.notification.decline")) {
        @Override
        public void actionPerformed(AnActionEvent event) {
          notification.expire();
          setCanReportAnalaytics(false);
        }
      });
      Notifications.Bus.notify(notification);
    }
    else {
      getAnalytics();
    }
  }
}

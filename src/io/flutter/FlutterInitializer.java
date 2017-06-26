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
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import io.flutter.analytics.Analytics;
import io.flutter.analytics.ToolWindowTracker;
import io.flutter.android.AndroidSdk;
import io.flutter.dart.DartfmtSettings;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterRunNotifications;
import io.flutter.run.daemon.DeviceService;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.view.FlutterViewFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.UUID;

/**
 * Runs actions after the project has started up and the index is up to date.
 *
 * @see ProjectOpenActivity for actions that run earlier.
 * @see io.flutter.project.FlutterProjectOpenProcessor for additional actions that
 * may run when a project is being imported.
 */
public class FlutterInitializer implements StartupActivity {
  private static final String analyticsClientIdKey = "io.flutter.analytics.clientId";
  private static final String analyticsOptOutKey = "io.flutter.analytics.optOut";
  private static final String analyticsToastShown = "io.flutter.analytics.toastShown";
  private static final String verboseLoggingKey = "io.flutter.verboseLogging";

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
      final ApplicationInfo info = ApplicationInfo.getInstance();
      analytics = new Analytics(clientId, descriptor.getVersion(), info.getVersionName(), info.getFullVersion());

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

  public static void sendAnalyticsAction(@NotNull AnAction action) {
    sendAnalyticsAction(action.getClass().getSimpleName());
  }

  public static void sendAnalyticsAction(@NotNull String name) {
    getAnalytics().sendEvent("intellij", name);
  }

  public static boolean isVerboseLogging() {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    return properties.getBoolean(verboseLoggingKey, false);
  }

  public static void setVerboseLogging(boolean value) {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(verboseLoggingKey, value);
  }

  @Override
  public void runActivity(@NotNull Project project) {
    // Start watching for devices.
    DeviceService.getInstance(project);

    // Start watching for Flutter debug active events.
    FlutterViewFactory.init(project);

    final PubRoot root = PubRoot.forProjectWithRefresh(project);
    if (root != null) {
      if (root.hasAndroidModule(project)) {
        ensureAndroidSdk(project);
      }

      FlutterModuleUtils.autoCreateRunConfig(project, root);
      FlutterModuleUtils.autoShowMain(project, root);
    }

    FlutterRunNotifications.init(project);

    // Do a one-time set for the default value of the whole file dartfmt setting.
    if (DartfmtSettings.dartPluginHasSetting()) {
      if (!DartfmtSettings.hasBeenOneTimeSet()) {
        DartfmtSettings.setDartfmtValue();
      }
    }

    // Initialize the analytics notification group.
    NotificationsConfiguration.getNotificationsConfiguration().register(
      Analytics.GROUP_DISPLAY_ID,
      NotificationDisplayType.STICKY_BALLOON,
      false);

    // Initialize analytics.
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (!properties.getBoolean(analyticsToastShown)) {
      properties.setValue(analyticsToastShown, true);

      final Notification notification = new Notification(
        Analytics.GROUP_DISPLAY_ID,
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
          final Analytics analytics = getAnalytics();
          // We only track for flutter projects.
          if (FlutterModuleUtils.usesFlutter(project)) {
            ToolWindowTracker.track(project, analytics);
          }
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
      // We only track for flutter projects.
      if (FlutterModuleUtils.usesFlutter(project)) {
        ToolWindowTracker.track(project, getAnalytics());
      }
    }
  }

  /**
   * Automatically set Android SDK based on ANDROID_HOME.
   */
  private void ensureAndroidSdk(@NotNull Project project) {
    if (ProjectRootManager.getInstance(project).getProjectSdk() != null) {
      return; // Don't override user's settings.
    }

    final AndroidSdk wanted = AndroidSdk.fromEnvironment();
    if (wanted == null) {
      return; // ANDROID_HOME not set or Android SDK not created in IDEA; not clear what to do.
    }

    ApplicationManager.getApplication().runWriteAction(() -> wanted.setCurrent(project));
  }
}

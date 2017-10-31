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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import io.flutter.analytics.Analytics;
import io.flutter.analytics.ToolWindowTracker;
import io.flutter.android.IntelliJAndroidSdk;
import io.flutter.dart.DartfmtSettings;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.FlutterRunNotifications;
import io.flutter.run.daemon.DeviceService;
import io.flutter.sdk.FlutterPluginsLibraryManager;
import io.flutter.settings.FlutterSettings;
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

      final IdeaPluginDescriptor descriptor = PluginManager.getPlugin(FlutterUtils.getPluginId());
      assert descriptor != null;
      final ApplicationInfo info = ApplicationInfo.getInstance();
      analytics = new Analytics(clientId, descriptor.getVersion(), info.getVersionName(), info.getFullVersion());

      // Set up reporting prefs.
      analytics.setCanSend(getCanReportAnalytics());

      // Send initial loading hit.
      analytics.sendScreenView("main");

      FlutterSettings.getInstance().sendSettingsToAnalytics(analytics);
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

  @Override
  public void runActivity(@NotNull Project project) {
    // Convert all modules of deprecated type FlutterModuleType.
    if (FlutterModuleUtils.convertFromDeprecatedModuleType(project)) {
      // If any modules were converted over, create a notification
      FlutterMessages.showInfo(FlutterBundle.message("flutter.initializer.module.converted.title"),
                               FlutterBundle.message("flutter.initializer.module.converted.content"));
    }

    // Start watching for devices.
    DeviceService.getInstance(project);

    // Start watching for Flutter debug active events.
    FlutterViewFactory.init(project);

    // If the project declares a Flutter dependency, do some extra initialization.
    final PubRoot root = PubRoot.singleForProjectWithRefresh(project);
    if (root != null && root.declaresFlutter()) {
      // Set Android SDK.
      if (root.hasAndroidModule(project)) {
        ensureAndroidSdk(project);
      }

      // Setup a default run configuration for 'main.dart' (if it exists).
      FlutterModuleUtils.autoCreateRunConfig(project, root);

      // If there are no open editors, show main.
      final FileEditorManager editorManager = FileEditorManager.getInstance(project);
      if (editorManager.getOpenFiles().length == 0) {
        FlutterModuleUtils.autoShowMain(project, root);
      }
    }

    FlutterRunNotifications.init(project);

    // Watch save actions.
    FlutterReloadManager.init(project);

    // Do a one-time set for the default value of the whole file dartfmt setting.
    if (DartfmtSettings.dartPluginHasSetting()) {
      if (!DartfmtSettings.hasBeenOneTimeSet()) {
        DartfmtSettings.setDartfmtValue();
      }
    }

    // Start watching for project structure and .packages file changes.
    final FlutterPluginsLibraryManager libraryManager = new FlutterPluginsLibraryManager(project);
    libraryManager.startWatching();

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
        "Welcome to Flutter!",
        FlutterBundle.message("flutter.analytics.notification.content"),
        NotificationType.INFORMATION,
        (notification1, event) -> {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            if ("url".equals(event.getDescription())) {
              BrowserUtil.browse("https://www.google.com/policies/privacy/");
            }
          }
        });
      notification.addAction(new AnAction("Sounds good!") {
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
      notification.addAction(new AnAction("No thanks") {
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

    final IntelliJAndroidSdk wanted = IntelliJAndroidSdk.fromEnvironment();
    if (wanted == null) {
      return; // ANDROID_HOME not set or Android SDK not created in IDEA; not clear what to do.
    }

    ApplicationManager.getApplication().runWriteAction(() -> wanted.setCurrent(project));
  }
}

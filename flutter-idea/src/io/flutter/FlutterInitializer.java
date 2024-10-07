/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.ProjectTopics;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import de.roderick.weberknecht.WebSocketException;
import io.flutter.analytics.Analytics;
import io.flutter.analytics.FlutterAnalysisServerListener;
import io.flutter.analytics.ToolWindowTracker;
import io.flutter.analytics.UnifiedAnalytics;
import io.flutter.android.IntelliJAndroidSdk;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DtdUtils;
import io.flutter.devtools.DevToolsExtensionsViewFactory;
import io.flutter.devtools.DevToolsUtils;
import io.flutter.devtools.RemainingDevToolsViewFactory;
import io.flutter.editor.FlutterSaveActionsManager;
import io.flutter.logging.FlutterConsoleLogManager;
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.performance.FlutterPerformanceViewFactory;
import io.flutter.preview.PreviewViewFactory;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.FlutterRunNotifications;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.run.daemon.DeviceService;
import io.flutter.sdk.FlutterPluginsLibraryManager;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.settings.FlutterSettings;
import io.flutter.survey.FlutterSurveyNotifications;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.view.FlutterViewFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs actions after the project has started up and the index is up to date.
 *
 * @see ProjectOpenActivity for actions that run earlier.
 * @see io.flutter.project.FlutterProjectOpenProcessor for additional actions that
 * may run when a project is being imported.
 */
public class FlutterInitializer implements StartupActivity {
  private static final Logger LOG = Logger.getInstance(FlutterInitializer.class);
  private static final String analyticsClientIdKey = "io.flutter.analytics.clientId";
  private static final String analyticsOptOutKey = "io.flutter.analytics.optOut";
  private static final String analyticsToastShown = "io.flutter.analytics.toastShown";

  private static Analytics analytics;

  private boolean toolWindowsInitialized = false;

  private boolean busSubscribed = false;

  private @NotNull AtomicLong lastScheduledThemeChangeTime = new AtomicLong();

  @Override
  public void runActivity(@NotNull Project project) {
    // Convert all modules of deprecated type FlutterModuleType.
    if (FlutterModuleUtils.convertFromDeprecatedModuleType(project)) {
      // If any modules were converted over, create a notification
      FlutterMessages.showInfo(
        FlutterBundle.message("flutter.initializer.module.converted.title"),
        "Converted from '" + FlutterModuleUtils.DEPRECATED_FLUTTER_MODULE_TYPE_ID +
        "' to '" + FlutterModuleUtils.getModuleTypeIDForFlutter() + "'.",
        project);
    }

    // Disable the 'Migrate Project to Gradle' notification.
    FlutterUtils.disableGradleProjectMigrationNotification(project);

    // Start watching for devices.
    DeviceService.getInstance(project);

    // Start a DevTools server
    DevToolsService.getInstance(project);

    // If the project declares a Flutter dependency, do some extra initialization.
    boolean hasFlutterModule = false;

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final boolean declaresFlutter = FlutterModuleUtils.declaresFlutter(module);

      hasFlutterModule = hasFlutterModule || declaresFlutter;

      if (!declaresFlutter) {
        continue;
      }

      // Ensure SDKs are configured; needed for clean module import.
      FlutterModuleUtils.enableDartSDK(module);

      for (PubRoot root : PubRoots.forModule(module)) {
        // Set Android SDK.
        if (root.hasAndroidModule(project)) {
          ensureAndroidSdk(project);
        }

        // Setup a default run configuration for 'main.dart' (if it's not there already and the file exists).
        FlutterModuleUtils.autoCreateRunConfig(project, root);

        // If there are no open editors, show main.
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        if (fileEditorManager != null && fileEditorManager.getOpenFiles().length == 0) {
          FlutterModuleUtils.autoShowMain(project, root);
        }
      }
    }

    if (hasFlutterModule || WorkspaceCache.getInstance(project).isBazel()) {
      initializeToolWindows(project);
    }
    else {
      project.getMessageBus().connect().subscribe(ProjectTopics.MODULES, new ModuleListener() {
        @Override
        public void moduleAdded(@NotNull Project project, @NotNull Module module) {
          if (!toolWindowsInitialized && FlutterModuleUtils.isFlutterModule(module)) {
            initializeToolWindows(project);
          }
        }
      });
    }

    if (hasFlutterModule
        && PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.android")) != null
        && !FlutterModuleUtils.hasAndroidModule(project)) {
      List<Module> modules = FlutterModuleUtils.findModulesWithFlutterContents(project);
      for (Module module : modules) {
        if (module.isDisposed() || !FlutterModuleUtils.isFlutterModule(module)) continue;
        VirtualFile moduleFile = module.getModuleFile();
        if (moduleFile == null) continue;
        VirtualFile baseDir = moduleFile.getParent();
        if (baseDir.getName().equals(".idea")) {
          baseDir = baseDir.getParent();
        }
        boolean isModule = false;
        try { // TODO(messick) Rewrite this loop to eliminate the need for this try-catch
          FlutterModuleBuilder.addAndroidModule(project, null, baseDir.getPath(), module.getName(), isModule);
        }
        catch (IllegalStateException ignored) {
        }
      }

      // Ensure a run config is selected and ready to go.
      FlutterModuleUtils.ensureRunConfigSelected(project);
    }

    FlutterRunNotifications.init(project);

    // Start watching for survey triggers.
    FlutterSurveyNotifications.init(project);

    // Start the widget perf manager.
    FlutterWidgetPerfManager.init(project);

    // Watch save actions for reload on save.
    FlutterReloadManager.init(project);

    // Watch save actions for format on save.
    FlutterSaveActionsManager.init(project);

    // Start watching for project structure changes.
    final FlutterPluginsLibraryManager libraryManager = new FlutterPluginsLibraryManager(project);
    libraryManager.startWatching();

    // Initialize the analytics notification group.
    NotificationsConfiguration.getNotificationsConfiguration().register(
      Analytics.GROUP_DISPLAY_ID,
      NotificationDisplayType.STICKY_BALLOON,
      false);

    // Set our preferred settings for the run console.
    FlutterConsoleLogManager.initConsolePreferences();

    // Initialize notifications for theme changes.
    setUpThemeChangeNotifications(project);

    // Send unsupported SDK notifications if relevant.
    checkSdkVersionNotification(project);

    setUpDtdAnalytics(project);

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
      boolean finalHasFlutterModule = hasFlutterModule;
      //noinspection DialogTitleCapitalization
      notification.addAction(new AnAction("Sounds good!") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          notification.expire();
          // We only track for flutter projects.
          if (finalHasFlutterModule) {
            enableAnalytics(project);
          }
        }
      });
      //noinspection DialogTitleCapitalization
      notification.addAction(new AnAction("No thanks") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          notification.expire();
          setCanReportAnalytics(false);
        }
      });
      Notifications.Bus.notify(notification, project);
    }
    else {
      // We only track for flutter projects.
      if (hasFlutterModule) {
        enableAnalytics(project);
      }
    }
  }

  private void setUpDtdAnalytics(Project project) {
    if (project == null) return;
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null || !sdk.getVersion().canUseDtd()) return;
    Thread t1 = new Thread(() -> {
      UnifiedAnalytics unifiedAnalytics = new UnifiedAnalytics(project);
      // TODO(helin24): Turn on after adding some unified analytics reporting.
      //unifiedAnalytics.manageConsent();
    });
    t1.start();
  }

  private void setUpThemeChangeNotifications(@NotNull Project project) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null || !sdk.getVersion().canUseDtd()) return;
    Thread t1 = new Thread(() -> {
      if (busSubscribed) return;
      final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
      connection.subscribe(EditorColorsManager.TOPIC, (EditorColorsListener)scheme -> {
        sendThemeChangedEvent(project);
      });
      connection.subscribe(UISettingsListener.TOPIC, (UISettingsListener)scheme -> {
        sendThemeChangedEvent(project);
      });
      busSubscribed = true;
    });
    t1.start();
  }

  private void sendThemeChangedEvent(@NotNull Project project) {
    // Debounce this request because the topic subscriptions can trigger multiple times (potentially from initial notification of change and
    // also from application of change)

    // Set the current time of this request
    final long requestTime = System.currentTimeMillis();
    lastScheduledThemeChangeTime.set(requestTime);

    // Schedule event to be sent in a second if nothing more recent has come in.
    Executors.newSingleThreadScheduledExecutor().schedule(() -> {
      if (lastScheduledThemeChangeTime.get() != requestTime) {
        // A more recent request has been set, so drop this request.
        return;
      }

      final JsonObject params = new JsonObject();
      params.addProperty("eventKind", "themeChanged");
      params.addProperty("streamId", "Editor");

      final JsonObject themeData = new JsonObject();
      final DevToolsUtils utils = new DevToolsUtils();
      themeData.addProperty("isDarkMode", Boolean.FALSE.equals(utils.getIsBackgroundBright()));
      themeData.addProperty("backgroundColor", utils.getColorHexCode());
      themeData.addProperty("fontSize", utils.getFontSize().intValue());

      final JsonObject eventData = new JsonObject();
      eventData.add("theme", themeData);
      params.add("eventData", eventData);

      try {
        final DtdUtils dtdUtils = new DtdUtils();
        final DartToolingDaemonService dtdService = dtdUtils.readyDtdService(project).get();
        if (dtdService == null) {
          LOG.error("Unable to send theme changed event because DTD service is null");
          return;
        }

        dtdService.sendRequest("postEvent", params, false, object -> {
                                 JsonObject result = object.getAsJsonObject("result");
                                 if (result == null) {
                                   LOG.error("Theme changed event returned null result");
                                   return;
                                 }
                                 JsonPrimitive type = result.getAsJsonPrimitive("type");
                                 if (type == null) {
                                   LOG.error("Theme changed event result type is null");
                                   return;
                                 }
                                 if (!"Success".equals(type.getAsString())) {
                                   LOG.error("Theme changed event result: " + type.getAsString());
                                 }
                               }
        );
      }
      catch (WebSocketException | InterruptedException | ExecutionException e) {
        LOG.error("Unable to send theme changed event", e);
      }
    }, 1, TimeUnit.SECONDS);
  }

  private void checkSdkVersionNotification(@NotNull Project project) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) return;
    final FlutterSdkVersion version = sdk.getVersion();

    if (!version.sdkIsSupported() && version.getVersionText() != null) {
      final FlutterSettings settings = FlutterSettings.getInstance();
      if (settings == null || settings.isSdkVersionOutdatedWarningAcknowledged(version.getVersionText())) return;

      ApplicationManager.getApplication().invokeLater(() -> {
        final Notification notification = new Notification("flutter-sdk",
                                                           "Flutter SDK requires update",
                                                           "Support for v" +
                                                           version.getVersionText() +
                                                           " of the Flutter SDK will be removed in an upcoming release of the Flutter plugin. Consider updating to a more recent Flutter SDK",
                                                           NotificationType.WARNING);
        notification.addAction(new AnAction("More Info") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent event) {
            // TODO(helin24): Update with informational URL.
            BrowserLauncher.getInstance().browse("https://www.google.com", null);
            settings.setSdkVersionOutdatedWarningAcknowledged(version.getVersionText(), true);
            notification.expire();
          }
        });

        notification.addAction(new AnAction("I understand") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent event) {
            settings.setSdkVersionOutdatedWarningAcknowledged(version.getVersionText(), true);
            notification.expire();
          }
        });
        Notifications.Bus.notify(notification, project);
      });
    }
  }

  private static void enableAnalytics(@NotNull Project project) {
    ToolWindowTracker.track(project, getAnalytics());
    DartAnalysisServerService.getInstance(project).addAnalysisServerListener(FlutterAnalysisServerListener.getInstance(project));
  }

  private void initializeToolWindows(@NotNull Project project) {
    // Start watching for Flutter debug active events.
    FlutterViewFactory.init(project);
    FlutterPerformanceViewFactory.init(project);
    PreviewViewFactory.init(project);
    RemainingDevToolsViewFactory.init(project);
    DevToolsExtensionsViewFactory.init(project);
    toolWindowsInitialized = true;
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

  /**
   * This method is only used for testing.
   */
  @VisibleForTesting
  public static void setAnalytics(Analytics inAnalytics) {
    analytics = inAnalytics;
  }

  @NotNull
  public static Analytics getAnalytics() {
    if (analytics == null) {
      final PropertiesComponent properties = PropertiesComponent.getInstance();

      String clientId = properties.getValue(analyticsClientIdKey);
      if (clientId == null) {
        clientId = UUID.randomUUID().toString();
        properties.setValue(analyticsClientIdKey, clientId);
      }

      final IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(FlutterUtils.getPluginId());
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

  public static boolean getCanReportAnalytics() {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    return !properties.getBoolean(analyticsOptOutKey, false);
  }

  public static void setCanReportAnalytics(boolean canReportAnalytics) {
    if (getCanReportAnalytics() != canReportAnalytics) {
      final boolean wasReporting = getCanReportAnalytics();

      final PropertiesComponent properties = PropertiesComponent.getInstance();
      properties.setValue(analyticsOptOutKey, !canReportAnalytics);
      if (analytics != null) {
        analytics.setCanSend(getCanReportAnalytics());
      }

      if (!wasReporting && canReportAnalytics) {
        getAnalytics().sendScreenView("main");
      }
    }
  }

  public static void sendAnalyticsAction(@NotNull AnAction action) {
    sendAnalyticsAction(action.getClass().getSimpleName());
  }

  public static void sendAnalyticsAction(@NotNull String name) {
    getAnalytics().sendEvent("intellij", name);
  }
}

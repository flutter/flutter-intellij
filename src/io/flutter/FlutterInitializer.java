/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import de.roderick.weberknecht.WebSocketException;
import io.flutter.android.IntelliJAndroidSdk;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DtdUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.devtools.DevToolsExtensionsViewFactory;
import io.flutter.devtools.DevToolsUtils;
import io.flutter.devtools.RemainingDevToolsViewFactory;
import io.flutter.editor.FlutterSaveActionsManager;
import io.flutter.logging.FlutterConsoleLogManager;
import io.flutter.logging.PluginLogger;
import io.flutter.module.FlutterModuleBuilder;
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
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.InspectorViewFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs actions after the project has started up and the index is up to date.
 *
 * @see ProjectOpenActivity for actions that run earlier.
 * @see io.flutter.project.FlutterProjectOpenProcessor for additional actions that
 * may run when a project is being imported.
 */
public class FlutterInitializer extends FlutterProjectActivity {
  private boolean toolWindowsInitialized = false;

  private boolean busSubscribed = false;

  private @NotNull AtomicLong lastScheduledThemeChangeTime = new AtomicLong();

  // Shared scheduler to avoid creating/closing executors on EDT
  @NotNull
  private final ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();

  @Override
  public void executeProjectStartup(@NotNull Project project) {
    log().info("Executing Flutter plugin startup for project: " + project.getName());
    // Disable the 'Migrate Project to Gradle' notification.
    FlutterUtils.disableGradleProjectMigrationNotification(project);

    // Start watching for devices.
    DeviceService.getInstance(project);

    // Start a DevTools server
    DevToolsService.getInstance(project);

    // If the project declares a Flutter dependency, do some extra initialization.
    boolean hasFlutterModule = false;


    var roots = new ArrayList<PubRoot>();

    for (Module module : OpenApiUtils.getModules(project)) {
      final boolean declaresFlutter = FlutterModuleUtils.declaresFlutter(module);

      hasFlutterModule = hasFlutterModule || declaresFlutter;

      if (!declaresFlutter) {
        continue;
      }

      log().info("Flutter module has been found for project: " + project.getName());
      // Ensure SDKs are configured; needed for clean module import.
      FlutterModuleUtils.enableDartSDK(module);

      for (PubRoot root : PubRoots.forModule(module)) {
        // Set Android SDK.
        if (root.hasAndroidModule(project)) {
          ensureAndroidSdk(project);
        }

        // Collect the root for later processing in a read context.
        roots.add(root);

        // If there are no open editors, show main.
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        if (fileEditorManager != null && fileEditorManager.getOpenFiles().length == 0) {
          FlutterModuleUtils.autoShowMain(project, root);
        }
      }
    }

    // Lambdas need final vars.
    boolean finalHasFlutterModule = hasFlutterModule;
    ReadAction.nonBlocking(() -> {
        for (var root : roots) {
          // Set up a default run configuration for 'main.dart' (if it's not there already and the file exists).
          FlutterModuleUtils.autoCreateRunConfig(project, root);
        }
        return null;
      })
      .expireWith(FlutterDartAnalysisServer.getInstance(project))
      .finishOnUiThread(ModalityState.defaultModalityState(), result -> {
        edtInitialization(finalHasFlutterModule, project);
      })
      .submit(AppExecutorUtil.getAppExecutorService());

    // This sets the correct log level and listens for future changes.
    PluginLogger.updateLogLevel();
    FlutterSettings.getInstance().addListener(PluginLogger::updateLogLevel);
  }

  /***
   * Initialization that needs to complete on the EDT thread.
   */
  private void edtInitialization(boolean hasFlutterModule, @NotNull Project project) {
    if (hasFlutterModule || WorkspaceCache.getInstance(project).isBazel()) {
      initializeToolWindows(project);
    }
    else {
      project.getMessageBus().connect().subscribe(ModuleListener.TOPIC, new ModuleListener() {
        @Override
        public void modulesAdded(@NotNull Project project, @NotNull List<? extends Module> modules) {
          if (!toolWindowsInitialized && modules.stream().anyMatch(FlutterModuleUtils::isFlutterModule)) {
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

    // Watch save actions for reload on save.
    FlutterReloadManager.init(project);

    // Watch save actions for format on save.
    FlutterSaveActionsManager.init(project);

    // Start watching for project structure changes.
    final FlutterPluginsLibraryManager libraryManager = new FlutterPluginsLibraryManager(project);
    libraryManager.startWatching();

    // Set our preferred settings for the run console.
    FlutterConsoleLogManager.initConsolePreferences();

    // Initialize notifications for theme changes.
    setUpThemeChangeNotifications(project);

    // TODO(jwren) For releases in early H1 2025, include this message as well as a new one if the user is using a Flutter SDK version that
    //  is not supported, i.e. match VS Code implementation.
    // Send unsupported SDK notifications if relevant.
    checkSdkVersionNotification(project);

    showAndroidStudioBotNotification(project);

    setUpDtdAnalytics(project);
  }

  private void setUpDtdAnalytics(Project project) {
    if (project == null) return;
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null || !sdk.getVersion().canUseDtd()) return;
    //Thread t1 = new Thread(() -> {
    //  UnifiedAnalytics unifiedAnalytics = new UnifiedAnalytics(project);
    //   TODO(helin24): Turn on after adding some unified analytics reporting.
    //unifiedAnalytics.manageConsent();
    //});
    //t1.start();
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
    scheduler.schedule(() -> {
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

      final DtdUtils dtdUtils = new DtdUtils();
      dtdUtils.readyDtdService(project)
        .thenAccept(dtdService -> {
          if (dtdService == null) {
            log().warn("Unable to send theme changed event because DTD service is null");
            return;
          }
          try {
            dtdService.sendRequest("postEvent", params, false, object -> {
              JsonObject result = object.getAsJsonObject("result");
              if (result == null) {
                log().error("Theme changed event returned null result");
                return;
              }
              JsonPrimitive type = result.getAsJsonPrimitive("type");
              if (type == null) {
                log().error("Theme changed event result type is null");
                return;
              }
              if (!"Success".equals(type.getAsString())) {
                log().error("Theme changed event result: " + type.getAsString());
              }
            });
          }
          catch (WebSocketException e) {
            log().error("Unable to send theme changed event", e);
          }
        })
        .exceptionally(e -> {
          log().debug("DTD not ready; skipping themeChanged event", e);
          return null;
        });
    }, 1, TimeUnit.SECONDS);
  }

  private void checkSdkVersionNotification(@NotNull Project project) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) return;
    final FlutterSdkVersion version = sdk.getVersion();

    // See FlutterSdkVersion.MIN_SDK_SUPPORTED.
    if (version.isValid() && !version.isSDKSupported()) {
      final FlutterSettings settings = FlutterSettings.getInstance();
      if (settings.isSdkVersionOutdatedWarningAcknowledged(version.getVersionText(), false)) return;
      OpenApiUtils.safeInvokeLater(() -> {
        final Notification notification = new Notification(FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
                                                           "Flutter SDK requires update",
                                                           "Support for v" +
                                                           version.getVersionText() +
                                                           " of the Flutter SDK has been removed and we are no longer fixing issues that" +
                                                           " result from this version. Consider updating to a more recent Flutter SDK.",
                                                           NotificationType.WARNING);

        notification.addAction(new AnAction("Dismiss") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent event) {
            settings.setSdkVersionOutdatedWarningAcknowledged(version.getVersionText(), false, true);
            notification.expire();
          }
        });
        Notifications.Bus.notify(notification, project);
      });
      return;
    }

    // See FlutterSdkVersion.MIN_SDK_WITHOUT_SUNSET_WARNING.
    if (version.isValid() && version.isSDKAboutToSunset()) {
      final FlutterSettings settings = FlutterSettings.getInstance();
      if (settings.isSdkVersionOutdatedWarningAcknowledged(version.getVersionText(), true)) return;

      OpenApiUtils.safeInvokeLater(() -> {
        final Notification notification = new Notification(FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
                                                           "Flutter SDK requires update",
                                                           "Support for v" +
                                                           version.getVersionText() +
                                                           " of the Flutter SDK will be removed in an upcoming release of the Flutter " +
                                                           "plugin. Consider updating to a more recent Flutter SDK.",
                                                           NotificationType.WARNING);
        // TODO(jwren) If we can get a URL on the Flutter website with the appropriate information, we should include it in the
        //  notification as an action:
        // notification.addAction(new AnAction("More Info") {
        //  @Override
        //  public void actionPerformed(@NotNull AnActionEvent event) {
        //    // TODO(helin24): Update with informational URL.
        //    BrowserLauncher.getInstance().browse("https://www.google.com", null);
        //    settings.setSdkVersionOutdatedWarningAcknowledged(version.getVersionText(), true);
        //    notification.expire();
        //  }
        //});

        notification.addAction(new AnAction("Dismiss") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent event) {
            settings.setSdkVersionOutdatedWarningAcknowledged(version.getVersionText(), true, true);
            notification.expire();
          }
        });
        Notifications.Bus.notify(notification, project);
      });
    }
  }

  private void showAndroidStudioBotNotification(@NotNull Project project) {
    // Return if not a Flutter project
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) return;

    // Return if not in Android Studio
    if (!FlutterUtils.isAndroidStudio()) return;

    // Return if notification has been shown already
    final FlutterSettings settings = FlutterSettings.getInstance();
    if (settings.isAndroidStudioBotAcknowledged()) return;

    ApplicationManager.getApplication().invokeLater(() -> {
      //noinspection DialogTitleCapitalization
      final Notification notification = new Notification(FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
                                                         "Try Gemini in Android Studio",
                                                         "",
                                                         NotificationType.INFORMATION);
      notification.addAction(new AnAction("More Info") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          BrowserLauncher.getInstance().browse("https://developer.android.com/gemini-in-android", null);
          settings.setAndroidStudioBotAcknowledgedKey(true);
          notification.expire();
        }
      });
      notification.addAction(new AnAction("Dismiss") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          settings.setAndroidStudioBotAcknowledgedKey(true);
          notification.expire();
        }
      });
      Notifications.Bus.notify(notification, project);
    });
  }

  private void initializeToolWindows(@NotNull final Project project) {
    // Start watching for Flutter debug active events.
    InspectorViewFactory.init(project);
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

    OpenApiUtils.safeRunWriteAction(() -> wanted.setCurrent(project));
  }
}

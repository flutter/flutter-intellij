/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;
import io.flutter.FlutterMessages;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.console.FlutterConsoles;
import io.flutter.jxbrowser.EmbeddedBrowser;
import io.flutter.run.daemon.DaemonApi;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manage installing and opening DevTools.
 */
public class DevToolsManager {
  public static DevToolsManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DevToolsManager.class);
  }

  private static final Logger LOG = Logger.getInstance(DevToolsManager.class);

  private final Project project;

  private boolean installedDevTools = false;

  private DevToolsInstance devToolsInstance;

  private DevToolsManager(@NotNull Project project) {
    this.project = project;
  }

  public boolean hasInstalledDevTools() {
    return installedDevTools;
  }

  public CompletableFuture<Boolean> installDevTools() {
    if (isBazel(project)) {
      // TODO(jacobr): prebuild devtools so the initial devtools load is faster.
      // Bazel projects do not need to load DevTools.
      return createCompletedFuture(true);
    }

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return createCompletedFuture(false);
    }

    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    final FlutterCommand command = sdk.flutterPub(null, "global", "activate", "devtools");

    final ProgressManager progressManager = ProgressManager.getInstance();
    //noinspection DialogTitleCapitalization
    progressManager.run(new Task.Backgroundable(project, "Installing DevTools...", true) {
      Process process;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(getTitle());
        indicator.setIndeterminate(true);

        process = command.start((ProcessOutput output) -> {
          if (output.getExitCode() != 0) {
            final String message = (output.getStdout() + "\n" + output.getStderr()).trim();
            FlutterConsoles.displayMessage(project, null, message, true);
          }
        }, null);

        try {
          final int resultCode = process.waitFor();
          if (resultCode == 0) {
            installedDevTools = true;
          }
          result.complete(resultCode == 0);
        }
        catch (RuntimeException | InterruptedException re) {
          if (!result.isDone()) {
            result.complete(false);
          }
        }

        process = null;
      }

      @Override
      public void onCancel() {
        if (process != null && process.isAlive()) {
          process.destroy();
          if (!result.isDone()) {
            result.complete(false);
          }
        }
      }
    });

    return result;
  }

  public void openBrowser(FlutterApp app) {
    openBrowserImpl(app, null, null);
  }

  public void openBrowserAndConnect(FlutterApp app, String uri) {
    openBrowserAndConnect(app, uri, null);
  }

  public void openBrowserAndConnect(FlutterApp app, String uri, String screen) {
    openBrowserImpl(app, uri, screen);
  }

  /**
   * Open a browser connected to DevTools with the given (optional) screen.
   * <p>
   * Calling this method may cause DevTools to be installed (in which case, this API will provide
   * appropriate progress feedback to the user).
   */
  public void openToScreen(@NotNull FlutterApp app, @Nullable String screen) {
    // TODO(devoncarew): Provide feedback through the API about whether the app launch worked.

    if (hasInstalledDevTools()) {
      openBrowserAndConnect(app, app.getConnector().getBrowserUrl(), screen);
    }
    else {
      final CompletableFuture<Boolean> result = installDevTools();
      result.thenAccept(o -> openBrowserAndConnect(app, app.getConnector().getBrowserUrl(), screen));
    }
  }

  /**
   * Gets the process handler that will start DevTools from pub.
   */
  @Nullable
  private OSProcessHandler getProcessHandlerForPub() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    final FlutterCommand command = sdk.flutterPub(null, "global", "run", "devtools", "--machine", "--port=0");
    final OSProcessHandler processHandler = command.startProcessOrShowError(project);

    if (processHandler != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        //noinspection Convert2MethodRef
        processHandler.startNotify();
      });
    }
    return processHandler;
  }

  private boolean isBazel(Project project) {
    return WorkspaceCache.getInstance(project).isBazel();
  }

  public void openBrowserIntoPanel(FlutterApp app, String uri, ContentManager contentManager, String tabName, String pageName) {
    final String screen = null;

    if (isBazel(project)) {
      try {
        getDevToolsInstance(app).get(15, TimeUnit.SECONDS).openPanel(project, uri, contentManager, tabName, pageName);
      }
      catch (Exception e) {
        LOG.info("Failed to get existing devToolsInstance");
      }
    }
    else {
      if (devToolsInstance != null) {
        devToolsInstance.openPanel(project, uri, contentManager, tabName, pageName);
      }
      else {
        @Nullable final OSProcessHandler handler = getProcessHandlerForPub();

        if (handler != null) {
          // start the server
          DevToolsInstance.startServer(handler, instance -> {
            devToolsInstance = instance;

            devToolsInstance.openPanel(project, uri, contentManager, tabName, pageName);
          }, () -> {
            // Listen for closing; null out the devToolsInstance.
            devToolsInstance = null;
          }, project);
        }
      }
    }
  }

  private void openBrowserImpl(FlutterApp app, String uri, String screen) {
    // For internal users, we can connect to the DevTools server started by flutter daemon. For external users, the flutter daemon has an
    // older version of DevTools, so we launch the server using `pub global run` instead.
    if (isBazel(project)) {
      try {
        getDevToolsInstance(app).get(15, TimeUnit.SECONDS).openBrowserAndConnect(uri, screen);
      }
      catch (Exception e) {
        LOG.info("Failed to get existing devToolsInstance");
      }
    }
    else {
      if (devToolsInstance != null) {
        devToolsInstance.openBrowserAndConnect(uri, screen);
      }
      else {
        @Nullable final OSProcessHandler handler = getProcessHandlerForPub();
        startDevToolsServerAndConnect(handler, uri, screen);
      }
    }
  }

  private CompletableFuture<DevToolsInstance> getDevToolsInstance(FlutterApp app) {
    final CompletableFuture<DevToolsInstance> instance = new CompletableFuture<>();

    app.serveDevTools().thenAccept((DaemonApi.DevToolsAddress address) -> {
      if (!project.isOpen()) {
        // We should skip starting DevTools (and doing any UI work) if the project has been closed.
        return;
      }
      if (address == null) {
        LOG.error("DevTools address was null");
        instance.complete(null);
      }
      else {
        instance.complete(new DevToolsInstance(address.host, address.port));
      }
    });

    return instance;
  }

  private void startDevToolsServerAndConnect(OSProcessHandler handler, String uri, String screen) {
    if (handler != null) {
      // TODO(devoncarew) Add a Task.Backgroundable here; "Starting devtools..."

      // start the server
      DevToolsInstance.startServer(handler, instance -> {
        devToolsInstance = instance;

        devToolsInstance.openBrowserAndConnect(uri, screen);
      }, () -> {
        // Listen for closing; null out the devToolsInstance.
        devToolsInstance = null;
      }, project);
    }
    else {
      // TODO(devoncarew): We should provide feedback to callers that the open browser call failed.
      devToolsInstance = null;
    }
  }

  private CompletableFuture<Boolean> createCompletedFuture(boolean value) {
    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    result.complete(value);
    return result;
  }
}

class DevToolsInstance {
  public static void startServer(
    @NotNull OSProcessHandler processHandler,
    @NotNull Callback<DevToolsInstance> onSuccess,
    @NotNull Runnable onClose,
    @NotNull Project projectContext
  ) {
    //noinspection DialogTitleCapitalization
    final Notification notification = new Notification(
      FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
      "Installing DevTools...",
      "The DevTools build is in progress",
      NotificationType.INFORMATION);

    // We only want to show the notification if installation is taking a while.
    // If the notification is expired by time notify is called, it will not appear.
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.schedule(() -> Notifications.Bus.notify(notification, projectContext), 2, TimeUnit.SECONDS);

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        final String text = event.getText().trim();

        if (text.startsWith("{") && text.endsWith("}")) {
          // {"event":"server.started","params":{"host":"127.0.0.1","port":9100}}

          try {
            final JsonElement element = JsonUtils.parseString(text);

            // params.port
            final JsonObject obj = element.getAsJsonObject();
            final JsonObject params = obj.getAsJsonObject("params");
            final String host = JsonUtils.getStringMember(params, "host");
            final int port = JsonUtils.getIntMember(params, "port");

            if (port != -1) {
              final DevToolsInstance instance = new DevToolsInstance(host, port);
              onSuccess.call(instance);
            }
            else {
              processHandler.destroyProcess();
            }
          }
          catch (JsonSyntaxException e) {
            processHandler.destroyProcess();
          }
          notification.expire();
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        onClose.run();
      }
    });
  }

  final String devtoolsHost;
  final int devtoolsPort;

  DevToolsInstance(String devtoolsHost, int devtoolsPort) {
    this.devtoolsHost = devtoolsHost;
    this.devtoolsPort = devtoolsPort;
  }

  public void openBrowserAndConnect(String serviceProtocolUri, String page) {
    BrowserLauncher.getInstance().browse(
      DevToolsUtils.generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false),
      null
    );
  }

  public void openPanel(Project project, String serviceProtocolUri, ContentManager contentManager, String tabName, String pageName) {
    final String color = ColorUtil.toHex(UIUtil.getEditorPaneBackground());
    final String url = DevToolsUtils.generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, pageName, true, color);

    //noinspection CodeBlock2Expr
    ApplicationManager.getApplication().invokeLater(() -> {
      EmbeddedBrowser.getInstance(project).openPanel(contentManager, tabName, url);
    });
  }
}

interface Callback<T> {
  void call(T value);
}

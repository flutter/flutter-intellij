/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.console.FlutterConsoles;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

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
      // Bazel projects do not need to load DevTools.
      return createCompletedFuture(true);
    }
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return createCompletedFuture(false);
    }

    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    // TODO(https://github.com/flutter/flutter/issues/33324): We shouldn't need a pubroot to call pub global.
    @Nullable final PubRoot pubRoot = PubRoots.forProject(project).stream().findFirst().orElse(null);
    final FlutterCommand command = sdk.flutterPackagesPub(pubRoot, "global", "activate", "devtools");

    final ProgressManager progressManager = ProgressManager.getInstance();
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

  public void openBrowser() {
    openBrowserImpl(null, null);
  }

  public void openBrowserAndConnect(String uri) {
    openBrowserAndConnect(uri, null);
  }

  public void openBrowserAndConnect(String uri, String page) {
    openBrowserImpl(uri, page);
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
    // TODO(https://github.com/flutter/flutter/issues/33324): We shouldn't need a pubroot to call pub global.
    @Nullable final PubRoot pubRoot = PubRoots.forProject(project).stream().findFirst().orElse(null);
    final FlutterCommand command = sdk.flutterPackagesPub(pubRoot, "global", "run", "devtools", "--machine", "--port=0");

    // TODO(devoncarew): Refactor this so that we don't use the console to display output - this steals
    // focus away from the Run (or Debug) view.
    return command.startInConsole(project);
  }

  /**
   * Gets the process handler that will start DevTools in bazel.
   */
  @Nullable
  private OSProcessHandler getProcessHandlerForBazel() {
    final Workspace workspace = Workspace.load(project);
    if (workspace == null || workspace.getDevtoolsScript() == null) {
      return null;
    }
    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setExePath(FileUtil.toSystemDependentName(workspace.getRoot().getPath() + "/" + workspace.getLaunchScript()));
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.addParameters(workspace.getDevtoolsScript(), "--", "--machine", "--port=0");
    OSProcessHandler handler;
    try {
      handler = new OSProcessHandler(commandLine);
    }
    catch (ExecutionException e) {
      FlutterUtils.warn(LOG, e);
      handler = null;
    }
    if (handler != null) {
      FlutterConsoles.displayProcessLater(handler, project, null, handler::startNotify);
    }
    return handler;
  }

  private boolean isBazel(Project project) {
    return FlutterSettings.getInstance().shouldUseBazel() && Workspace.load(project) != null;
  }

  private void openBrowserImpl(String uri, String page) {
    if (devToolsInstance != null) {
      devToolsInstance.openBrowserAndConnect(uri, page);
      return;
    }

    @Nullable
    final OSProcessHandler handler = isBazel(project) ? getProcessHandlerForBazel() : getProcessHandlerForPub();

    // start the server
    DevToolsInstance.startServer(handler, instance -> {
      devToolsInstance = instance;

      devToolsInstance.openBrowserAndConnect(uri, page);
    }, instance -> {
      // Listen for closing, null out the devToolsInstance.
      devToolsInstance = null;
    });
  }

  private CompletableFuture<Boolean> createCompletedFuture(boolean value) {
    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    result.complete(value);
    return result;
  }
}

class DevToolsInstance {
  public static void startServer(
    @Nullable OSProcessHandler processHandler,
    @NotNull Callback<DevToolsInstance> onSuccess,
    @NotNull Callback<DevToolsInstance> onClose
  ) {
    if (processHandler == null) {
      return;
    }

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        final String text = event.getText().trim();

        if (text.startsWith("{") && text.endsWith("}")) {
          // {"event":"server.started","params":{"host":"127.0.0.1","port":9100}}

          try {
            final JsonParser jsonParser = new JsonParser();
            final JsonElement element = jsonParser.parse(text);

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
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        onClose.call(null);
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
      DevToolsUtils.generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page),
      null
    );
  }
}

interface Callback<T> {
  void call(T value);
}

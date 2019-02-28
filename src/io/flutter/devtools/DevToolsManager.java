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
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manage installing and opening DevTools.
 */
public class DevToolsManager {
  public static DevToolsManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DevToolsManager.class);
  }

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
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return createCompletedFuture(false);
    }

    final List<PubRoot> pubRoots = PubRoots.forProject(project);
    if (pubRoots.isEmpty()) {
      return createCompletedFuture(false);
    }

    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    final FlutterCommand command = sdk.flutterPackagesPub(pubRoots.get(0), "global", "activate", "devtools");

    final ProgressManager progressManager = ProgressManager.getInstance();
    progressManager.run(new Task.Backgroundable(project, "Installing DevTools...", true) {
      OSProcessHandler processHandler;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(getTitle());

        processHandler = command.startInConsole(project);

        try {
          final boolean value = processHandler.waitFor();
          if (value) {
            installedDevTools = true;
          }
          result.complete(value);
        }
        catch (RuntimeException re) {
          if (!result.isDone()) {
            result.complete(false);
          }
        }

        processHandler = null;
      }

      @Override
      public void onCancel() {
        if (processHandler != null && !processHandler.isProcessTerminated()) {
          processHandler.destroyProcess();
          if (!result.isDone()) {
            result.complete(false);
          }
        }
      }
    });

    return result;
  }

  public void openBrowser() {
    openBrowserImpl(-1);
  }

  public void openBrowserAndConnect(int port) {
    openBrowserImpl(port);
  }

  private void openBrowserImpl(int port) {
    if (devToolsInstance != null) {
      devToolsInstance.openBrowserAndConnect(port);
      return;
    }

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return;
    }

    final List<PubRoot> pubRoots = PubRoots.forProject(project);
    if (pubRoots.isEmpty()) {
      return;
    }

    // start the server
    DevToolsInstance.startServer(project, sdk, pubRoots.get(0), instance -> {
      devToolsInstance = instance;

      devToolsInstance.openBrowserAndConnect(port);
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
    Project project,
    FlutterSdk sdk,
    PubRoot pubRoot,
    Callback<DevToolsInstance> onSuccess,
    Callback<DevToolsInstance> onClose
  ) {
    final FlutterCommand command = sdk.flutterPackagesPub(pubRoot, "global", "run", "devtools", "--machine", "--port=0");
    final OSProcessHandler processHandler = command.startInConsole(project);

    if (processHandler == null) {
      return;
    }

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        final String text = event.getText().trim();

        if (text.startsWith("{") && text.endsWith("}")) {
          // {"method":"server.started","params":{"host":"127.0.0.1","port":9100}}

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

  public void openBrowserAndConnect(int serviceProtocolPort) {
    if (serviceProtocolPort == -1) {
      BrowserLauncher.getInstance().browse("http://" + devtoolsHost + ":" + devtoolsPort + "/", null);
    }
    else {
      BrowserLauncher.getInstance().browse(
        "http://" + devtoolsHost + ":" + devtoolsPort + "/?port=" + serviceProtocolPort,
        null
      );
    }
  }
}

interface Callback<T> {
  void call(T value);
}

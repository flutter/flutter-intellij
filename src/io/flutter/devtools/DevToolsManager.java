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
import com.intellij.execution.process.*;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import io.flutter.bazel.Workspace;
import io.flutter.console.FlutterConsoles;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
    if (FlutterSettings.getInstance().shouldUseBazel()) {
      DartSdk dartSdk = DartSdk.getDartSdk(project);
      DartSdkUtil.getPubPath(dartSdk);

      final CompletableFuture<Boolean> result = new CompletableFuture<>();

      final ProgressManager progressManager = ProgressManager.getInstance();
      progressManager.run(new Task.Backgroundable(project, "Installing DevTools...", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setText(getTitle());
          indicator.setIndeterminate(true);

          GeneralCommandLine commandLine = new GeneralCommandLine(DartSdkUtil.getPubPath(dartSdk), "global", "activate", "devtools").withWorkDirectory(
            Workspace.load(project).getRoot().getPath()).withRedirectErrorStream(true).withParentEnvironmentType(
            GeneralCommandLine.ParentEnvironmentType.CONSOLE);
          OSProcessHandler process = null;
          try {
            process = new OSProcessHandler(commandLine);
          }
          catch (ExecutionException e) {
            e.printStackTrace();
            result.completeExceptionally(e);
          }
          if (process == null) {
            result.complete(false);
            return;
          }

          process.addProcessListener(new ProcessListener() {
            @Override
            public void startNotified(@NotNull ProcessEvent event) {

            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {

            }

            @Override
            public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {

            }

            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
              FlutterConsoles.displayMessage(project, null, event.getText(), false);
            }
          });

          FlutterConsoles.displayMessage(project, null, "Installing devtools...", true);
          boolean processResult = false;
          try {
            processResult = process.waitFor(10000);
          } catch (RuntimeException re) {
            if (!process.isProcessTerminated()) {
              result.completeExceptionally(re);
            }
          }
          if (processResult) {
            installedDevTools = true;
            FlutterConsoles.displayMessage(project, null, "Devtools installation completed", true);
            result.complete(true);
          } else {
            FlutterConsoles.displayMessage(project, null, "Devtools installation failed", true);
            result.complete(false);
          }
        }
      });

      return result;
    }

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

  private void openBrowserImpl(String uri, String page) {
    if (devToolsInstance != null) {
      devToolsInstance.openBrowserAndConnect(uri, page);
      return;
    }
    if (FlutterSettings.getInstance().shouldUseBazel()) {

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
    Project project,
    FlutterSdk sdk,
    PubRoot pubRoot,
    Callback<DevToolsInstance> onSuccess,
    Callback<DevToolsInstance> onClose
  ) {
    final FlutterCommand command = sdk.flutterPackagesPub(pubRoot, "global", "run", "devtools", "--machine", "--port=0");

    // TODO(devoncarew): Refactor this so that we don't use the console to display output - this steals
    // focus away from the Run (or Debug) view.
    final OSProcessHandler processHandler = command.startInConsole(project);

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
    if (serviceProtocolUri == null) {
      BrowserLauncher.getInstance().browse("http://" + devtoolsHost + ":" + devtoolsPort + "/?hide=debugger&", null);
    }
    else {
      try {
        final String urlParam = URLEncoder.encode(serviceProtocolUri, "UTF-8");
        final String pageParam = page == null ? "" : ("#" + page);

        BrowserLauncher.getInstance().browse(
          "http://" + devtoolsHost + ":" + devtoolsPort + "/?uri=" + urlParam + pageParam,
          null
        );
      }
      catch (UnsupportedEncodingException ignored) {
      }
    }
  }
}

interface Callback<T> {
  void call(T value);
}

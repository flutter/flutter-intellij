/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.lang.dart.ide.devtools.DartDevToolsService;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DtdUtils;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.JsonUtils;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

class DevToolsServerTask extends Task.Backgroundable {
  private static final Logger LOG = Logger.getInstance(DevToolsServerTask.class);
  public static final String LOCAL_DEVTOOLS_DIR = "flutter.local.devtools.dir";
  public static final String LOCAL_DEVTOOLS_ARGS = "flutter.local.devtools.args";
  public @Nullable String failureMessage = null;
  private @NotNull Project project;
  private final AtomicReference<CompletableFuture<DevToolsInstance>> devToolsFutureRef;

  private DaemonApi daemonApi;
  private ProcessHandler process;

  public DevToolsServerTask(@NotNull Project project,
                            @NotNull String title,
                            AtomicReference<CompletableFuture<DevToolsInstance>> devToolsFutureRef) {
    super(project, title, true);
    this.project = project;
    this.devToolsFutureRef = devToolsFutureRef;
  }

  @Override
  public void run(@NotNull ProgressIndicator progressIndicator) {
    try {
      progressIndicator.setFraction(30);
      progressIndicator.setText2("Init");

      // If DevTools is not supported, start the daemon instead.
      final boolean dartDevToolsSupported = dartSdkSupportsDartDevTools();
      if (!dartDevToolsSupported) {
        progressIndicator.setFraction(60);
        progressIndicator.setText2("Daemon set-up");
        setUpWithDaemon();
        return;
      }

      // If we are in a Bazel workspace, start the server.
      // Note: This is only for internal usages.
      final WorkspaceCache workspaceCache = WorkspaceCache.getInstance(project);
      if (workspaceCache.isBazel()) {
        progressIndicator.setFraction(60);
        progressIndicator.setText2("Starting server");
        setUpWithDart(createCommand(workspaceCache.get().getRoot().getPath(), workspaceCache.get().getDevToolsScript(),
                                    ImmutableList.of("--machine")));
        return;
      }

      // This is only for development to check integration with a locally run DevTools server.
      // To enable, follow the instructions in:
      // https://github.com/flutter/flutter-intellij/blob/master/CONTRIBUTING.md#developing-with-local-devtools
      final String localDevToolsDir = Registry.stringValue(LOCAL_DEVTOOLS_DIR);
      if (!localDevToolsDir.isEmpty()) {
        progressIndicator.setFraction(60);
        progressIndicator.setText2("Starting local server");
        setUpLocalServer(localDevToolsDir);
      }

      // If the Dart plugin does not start DevTools, then call `dart devtools` to start the server.
      final Boolean dartPluginStartsDevTools = true;
      if (!dartPluginStartsDevTools) {
        progressIndicator.setFraction(60);
        progressIndicator.setText2("Starting server");
        setUpWithDart(createCommand(DartSdk.getDartSdk(project).getHomePath(),
                                    DartSdk.getDartSdk(project).getHomePath() + File.separatorChar + "bin" + File.separatorChar + "dart",
                                    ImmutableList.of("devtools", "--machine")));
      }

      // Otherwise, wait for the Dart Plugin to start the DevTools server.
      final CompletableFuture<DevToolsInstance> devToolsFuture = checkForDartPluginInitiatedDevToolsWithRetries(progressIndicator);
      devToolsFuture.whenComplete((devTools, error) -> {
        if (error != null) {
          cancelWithError(new Exception(error));
        }
        else {
          devToolsFutureRef.get().complete(devTools);
        }
      });
    }
    catch (java.util.concurrent.ExecutionException | InterruptedException e) {
      cancelWithError(e);
    }
  }

  private Boolean dartSdkSupportsDartDevTools() {
    final DartSdk dartSdk = DartSdk.getDartSdk(project);
    if (dartSdk != null) {
      final Version version = Version.parseVersion(dartSdk.getVersion());
      assert version != null;
      return version.compareTo(2, 15, 0) >= 0;
    }
    return false;
  }

  @Override
  public void onThrowable(@NotNull Throwable error) {
    super.onThrowable(error);
    cancelWithError(new Exception(error));
  }

  private void setUpLocalServer(String localDevToolsDir) throws java.util.concurrent.ExecutionException, InterruptedException {
    final DtdUtils dtdUtils = new DtdUtils();
    final DartToolingDaemonService dtdService = dtdUtils.readyDtdService(project).get();
    final String dtdUri = dtdService.getUri();

    final List<String> args = new ArrayList<>();
    args.add("serve");
    args.add("--machine");
    args.add("--dtd-uri=" + dtdUri);
    final String localDevToolsArgs = Registry.stringValue(LOCAL_DEVTOOLS_ARGS);
    if (!localDevToolsArgs.isEmpty()) {
      args.addAll(Arrays.stream(localDevToolsArgs.split(" ")).toList());
    }

    setUpInDevMode(createCommand(localDevToolsDir, "dt", args));
  }

  private void setUpInDevMode(GeneralCommandLine command) {
    try {
      this.process = new MostlySilentColoredProcessHandler(command);
      this.process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          final String text = event.getText().trim();

          // Keep this printout so developers can see DevTools startup output in idea.log.
          System.out.println("DevTools startup: " + text);
          tryParseStartupText(text);
        }
      });
      process.startNotify();
    }
    catch (ExecutionException e) {
      cancelWithError(e);
    }
  }

  private void setUpWithDart(GeneralCommandLine command) {
    try {
      this.process = new MostlySilentColoredProcessHandler(command);
      this.process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          tryParseStartupText(event.getText().trim());
        }
      });
      process.startNotify();

      ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
        @Override
        public void projectClosing(@NotNull Project project) {
          devToolsFutureRef.set(null);
          process.destroyProcess();
        }
      });
    }
    catch (ExecutionException e) {
      cancelWithError(e);
    }
  }

  private void setUpWithDaemon() {
    try {
      final GeneralCommandLine command = chooseCommand(project);
      if (command == null) {
        cancelWithError("Unable to find daemon command for project");
        return;
      }
      this.process = new MostlySilentColoredProcessHandler(command);
      daemonApi = new DaemonApi(process);
      daemonApi.listen(process, new DevToolsService.DevToolsServiceListener());
      daemonApi.devToolsServe().thenAccept((DaemonApi.DevToolsAddress address) -> {
        if (!project.isOpen()) {
          // We should skip starting DevTools (and doing any UI work) if the project has been closed.
          return;
        }
        if (address == null) {
          cancelWithError("DevTools address was null");
        }
        else {
          devToolsFutureRef.get().complete(new DevToolsInstance(address.host, address.port));
        }
      });
    }
    catch (ExecutionException e) {
      cancelWithError(e);
    }

    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        devToolsFutureRef.set(null);

        try {
          daemonApi.daemonShutdown().get(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException | java.util.concurrent.ExecutionException | TimeoutException e) {
          LOG.error("DevTools daemon did not shut down normally: " + e);
          if (!process.isProcessTerminated()) {
            process.destroyProcess();
          }
        }
      }
    });
  }

  private CompletableFuture<DevToolsInstance> checkForDartPluginInitiatedDevToolsWithRetries(ProgressIndicator progressIndicator)
    throws InterruptedException {
    progressIndicator.setText2("Waiting for Dart Plugin");
    final CompletableFuture<DevToolsInstance> devToolsFuture = new CompletableFuture<>();

    final long msBetweenRetries = 1500;
    int retries = 0;
    while (retries < 10) {
      retries++;
      final double currentProgress = progressIndicator.getFraction();
      progressIndicator.setFraction(Math.min(currentProgress + 10, 95));

      final @Nullable DevToolsInstance devTools = createDevToolsInstanceFromDartPluginUri();
      if (devTools != null) {
        LOG.debug("Dart Plugin DevTools ready after " + retries + " tries.");
        devToolsFuture.complete(devTools);
        return devToolsFuture;
      }
      else {
        LOG.debug("Dart Plugin DevTools not ready after " + retries + " tries.");
        Thread.sleep(msBetweenRetries);
      }
    }

    devToolsFuture.completeExceptionally(new Exception("Timed out waiting for Dart Plugin to start DevTools."));
    return devToolsFuture;
  }

  private @Nullable DevToolsInstance createDevToolsInstanceFromDartPluginUri() {
    final String dartPluginUri = DartDevToolsService.getInstance(project).getDevToolsHostAndPort();
    if (dartPluginUri == null) {
      return null;
    }

    String[] parts = dartPluginUri.split(":");
    String host = parts[0];
    Integer port = Integer.parseInt(parts[1]);
    if (host == null || port == null) {
      return null;
    }

    return new DevToolsInstance(host, port);
  }

  private void tryParseStartupText(@NotNull String text) {
    if (text.startsWith("{") && text.endsWith("}")) {
      try {
        final JsonElement element = JsonUtils.parseString(text);

        final JsonObject obj = element.getAsJsonObject();

        if (Objects.equals(JsonUtils.getStringMember(obj, "event"), "server.started")) {
          final JsonObject params = obj.getAsJsonObject("params");
          final String host = JsonUtils.getStringMember(params, "host");
          final int port = JsonUtils.getIntMember(params, "port");

          if (port != -1) {
            devToolsFutureRef.get().complete(new DevToolsInstance(host, port));
          }
          else {
            cancelWithError("DevTools port was invalid");
          }
        }
      }
      catch (JsonSyntaxException e) {
        cancelWithError(e);
      }
    }
  }

  private static GeneralCommandLine chooseCommand(@NotNull final Project project) {
    // Use daemon script if this is a bazel project.
    final Workspace workspace = WorkspaceCache.getInstance(project).get();
    if (workspace != null) {
      final String script = workspace.getDaemonScript();
      if (script != null) {
        return createCommand(workspace.getRoot().getPath(), script, ImmutableList.of());
      }
    }

    // Otherwise, use the Flutter SDK.
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    try {
      final String path = FlutterSdkUtil.pathToFlutterTool(sdk.getHomePath());
      return createCommand(sdk.getHomePath(), path, ImmutableList.of("daemon"));
    }
    catch (ExecutionException e) {
      FlutterUtils.warn(LOG, "Unable to calculate command to start Flutter daemon", e);
      return null;
    }
  }

  private static GeneralCommandLine createCommand(String workDir, String command, List<String> arguments) {
    final GeneralCommandLine result = new GeneralCommandLine().withWorkDirectory(workDir);
    result.setCharset(StandardCharsets.UTF_8);
    result.setExePath(FileUtil.toSystemDependentName(command));
    result.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, (new FlutterSdkUtil()).getFlutterHostEnvValue());

    for (String argument : arguments) {
      result.addParameter(argument);
    }

    return result;
  }

  private void cancelWithError(String message) {
    cancelWithError(new Exception(message));
  }

  private void cancelWithError(Exception exception) {
    final String errorTitle = "DevTools server start-up failure.";
    FlutterUtils.warn(LOG, errorTitle, exception);
    devToolsFutureRef.get().completeExceptionally(new Exception(errorTitle));
    showErrorNotification(errorTitle, exception.getMessage());
    throw new ProcessCanceledException(exception);
  }

  private void showErrorNotification(String errorTitle, String errorDetails) {
    OpenApiUtils.safeInvokeLater(() -> {
      final Notification notification = new Notification(FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
                                                         "DevTools",
                                                         errorTitle + " " + errorDetails,
                                                         NotificationType.WARNING);

      notification.addAction(new AnAction("Dismiss") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          notification.expire();
        }
      });
      Notifications.Bus.notify(notification, project);
    });
  }
}

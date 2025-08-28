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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.lang.dart.ide.devtools.DartDevToolsService;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DtdUtils;
import io.flutter.logging.PluginLogger;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.JsonUtils;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

class DevToolsServerTask extends Task.Backgroundable {
  private @NotNull static final Logger LOG = PluginLogger.createLogger(DevToolsServerTask.class);
  public @NotNull static final String LOCAL_DEVTOOLS_DIR = "flutter.local.devtools.dir";
  public @NotNull static final String LOCAL_DEVTOOLS_ARGS = "flutter.local.devtools.args";
  private @NotNull final Project project;
  private @NotNull final AtomicReference<CompletableFuture<DevToolsInstance>> devToolsFutureRef;

  private @Nullable DaemonApi daemonApi;
  private @Nullable ProcessHandler process;

  public DevToolsServerTask(@NotNull Project project,
                            @NotNull String title,
                            @NotNull AtomicReference<CompletableFuture<DevToolsInstance>> devToolsFutureRef) {
    super(project, title, true);
    this.project = project;
    this.devToolsFutureRef = devToolsFutureRef;
  }

  @Override
  public void run(@NotNull ProgressIndicator progressIndicator) {
    try {
      progressIndicator.setFraction(30);
      progressIndicator.setText2("Init");
      LOG.info("Finding or starting DevTools");

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
      // https://github.com/flutter/flutter-intellij/blob/main/CONTRIBUTING.md#developing-with-local-devtools
      final String localDevToolsDir = Registry.stringValue(LOCAL_DEVTOOLS_DIR);
      if (!localDevToolsDir.isEmpty()) {
        LOG.info("Starting local DevTools server at: " + localDevToolsDir);
        progressIndicator.setFraction(60);
        progressIndicator.setText2("Starting local server");
        setUpLocalServer(localDevToolsDir);
      }

      // Wait for the Dart Plugin to start the DevTools server.
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

  @Override
  public void onThrowable(@NotNull Throwable error) {
    cancelWithError(new Exception(error));
  }

  private void setUpLocalServer(@NotNull String localDevToolsDir) throws java.util.concurrent.ExecutionException, InterruptedException {
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

  private void setUpInDevMode(@NotNull GeneralCommandLine command) {
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

  private CompletableFuture<DevToolsInstance> checkForDartPluginInitiatedDevToolsWithRetries(@NotNull ProgressIndicator progressIndicator)
    throws InterruptedException {
    progressIndicator.setText2("Waiting for server with DTD");
    final CompletableFuture<DevToolsInstance> devToolsFuture = new CompletableFuture<>();

    final long msBetweenRetries = 1500;
    int retries = 0;
    while (retries < 10) {
      retries++;
      final double currentProgress = progressIndicator.getFraction();
      progressIndicator.setFraction(Math.min(currentProgress + 10, 95));

      final @Nullable DevToolsInstance devTools = createDevToolsInstanceFromDartPluginUri();
      if (devTools != null) {
        LOG.debug("Dart plugin DevTools ready after " + retries + " tries.");
        devToolsFuture.complete(devTools);
        return devToolsFuture;
      }
      else {
        LOG.debug("Dart plugin DevTools not ready after " + retries + " tries.");
        Thread.sleep(msBetweenRetries);
      }
    }

    devToolsFuture.completeExceptionally(new Exception("Timed out waiting for Dart plugin to start DevTools."));
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
    if (host == null) {
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

  private static @NotNull GeneralCommandLine createCommand(String workDir, String command, List<String> arguments) {
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

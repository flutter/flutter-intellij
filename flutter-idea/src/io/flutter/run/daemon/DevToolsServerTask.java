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

  public DevToolsServerTask(@NotNull Project project, @NotNull String title, AtomicReference<CompletableFuture<DevToolsInstance>> devToolsFutureRef) {
    super(project, title, true);
    this.project = project;
    this.devToolsFutureRef = devToolsFutureRef;
  }

  @Override
  public void run(@NotNull ProgressIndicator progressIndicator) {
    progressIndicator.setFraction(30);
    progressIndicator.setText2("Init");

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    boolean dartDevToolsSupported = false;
    final DartSdk dartSdk = DartSdk.getDartSdk(project);
    if (dartSdk != null) {
      final Version version = Version.parseVersion(dartSdk.getVersion());
      assert version != null;
      dartDevToolsSupported = version.compareTo(2, 15, 0) >= 0;
    }

    if (dartDevToolsSupported) {
      // This condition means we can use `dart devtools` to start.
      final WorkspaceCache workspaceCache = WorkspaceCache.getInstance(project);
      if (workspaceCache.isBazel()) {
        // This is only for internal usages.
        progressIndicator.setFraction(60);
        progressIndicator.setText2("Running server");
        setUpWithDart(createCommand(workspaceCache.get().getRoot().getPath(), workspaceCache.get().getDevToolsScript(),
                                    ImmutableList.of("--machine")));
      }
      else {
        final String localDevToolsDir = Registry.stringValue(LOCAL_DEVTOOLS_DIR);
        if (!localDevToolsDir.isEmpty()) {
          // This is only for development to check integration with a locally run DevTools server.
          // To enable, follow the instructions in:
          // https://github.com/flutter/flutter-intellij/blob/master/CONTRIBUTING.md#developing-with-local-devtools
          final DtdUtils dtdUtils = new DtdUtils();
          try {
            progressIndicator.setFraction(60);
            progressIndicator.setText2("Local server");
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
          catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new RuntimeException(e);
          }
          return;
        }

        // The Dart plugin should start DevTools with DTD, so try to use this instance of DevTools before trying to start another.
        final String dartPluginUri = DartDevToolsService.getInstance(project).getDevToolsHostAndPort();
        if (dartPluginUri != null) {
          String[] parts = dartPluginUri.split(":");
          String host = parts[0];
          Integer port = Integer.parseInt(parts[1]);
          if (host != null && port != null) {
            devToolsFutureRef.get().complete(new DevToolsInstance(host, port));
            return;
          }
        }

        setUpWithDart(createCommand(DartSdk.getDartSdk(project).getHomePath(),
                                    DartSdk.getDartSdk(project).getHomePath() +
                                    File.separatorChar +
                                    "bin" +
                                    File.separatorChar +
                                    "dart",
                                    ImmutableList.of("devtools", "--machine")));
      }
    }
    else {
      progressIndicator.setFraction(60);
      progressIndicator.setText2("Local server");
      setUpWithDaemon();
    }
  }

  @Override
  public void onCancel() {
    super.onCancel();
    devToolsFutureRef.get().completeExceptionally(new Exception("DevTools start-up canceled."));
    maybeShowErrorNotification();
  }

  @Override
  public void onThrowable(@NotNull Throwable error) {
    super.onThrowable(error);
    cancelTask(new Exception(error));
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
      cancelTask(e);
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
      cancelTask(e);
    }
  }

  private void setUpWithDaemon() {
    try {
      final GeneralCommandLine command = chooseCommand(project);
      if (command == null) {
        cancelTask("Unable to find daemon command for project");
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
          cancelTask("DevTools address was null");
        }
        else {
          devToolsFutureRef.get().complete(new DevToolsInstance(address.host, address.port));
        }
      });
    }
    catch (ExecutionException e) {
      cancelTask(e);
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
            cancelTask("DevTools port was invalid");
          }
        }
      }
      catch (JsonSyntaxException e) {
        cancelTask(e);
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

  private void cancelTask(String message) {
    cancelTask(new Exception(message));
  }

  private void cancelTask(Exception exception) {
    FlutterUtils.warn(LOG, "DevTools server start-up canceled.", exception);
    failureMessage = exception.getMessage();
    throw new ProcessCanceledException(exception);
  }

  private void maybeShowErrorNotification() {
    // If there is no failure message, this means the user canceled the task themselves. Therefore, don't display an error.
    if (failureMessage == null) {
      return;
    }

    OpenApiUtils.safeInvokeLater(() -> {
      final Notification notification = new Notification(FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
                                                         "DevTools",
                                                         "DevTools failed to start with error: " + failureMessage,
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

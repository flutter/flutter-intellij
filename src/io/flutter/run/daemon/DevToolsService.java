/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
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
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.console.FlutterConsoles;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.JsonUtils;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class DevToolsService {
  private static final Logger LOG = Logger.getInstance(DevToolsService.class);

  private static class DevToolsServiceListener implements DaemonEvent.Listener {
  }

  @NotNull private final Project project;
  private DaemonApi daemonApi;
  private ProcessHandler process;
  private AtomicReference<CompletableFuture<DevToolsInstance>> devToolsFutureRef = new AtomicReference<>(null);

  @NotNull
  public static DevToolsService getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, DevToolsService.class);
  }

  private DevToolsService(@NotNull final Project project) {
    this.project = project;
  }

  public CompletableFuture<DevToolsInstance> getDevToolsInstance() {
    // Create instance if it doesn't exist yet, or if the previous attempt failed.
    if (devToolsFutureRef.compareAndSet(null, new CompletableFuture<>())) {
      startServer();
    }

    if (devToolsFutureRef.updateAndGet((future) -> {
      if (future.isCompletedExceptionally()) {
        return null;
      } else {
        return future;
      }
    }) == null) {
      devToolsFutureRef.set(new CompletableFuture<>());
      startServer();
    }

    return devToolsFutureRef.get();
  }

  private void startServer() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      // TODO(helinx): Also use `setUpWithDaemon` for later flutter SDK versions where the daemon request `devtools.serve` has been changed
      //  to use the latest DevTools server.
      if (WorkspaceCache.getInstance(project).isBazel()) {
        setUpWithDaemon();
      }
      else {
        // For earlier flutter versions we need to use pub directly to run the latest DevTools server.
        setUpWithPub();
      }
    });
  }

  private void setUpWithDaemon() {
    try {
      final GeneralCommandLine command = chooseCommand(project);
      if (command == null) {
        logExceptionAndComplete("Unable to find daemon command for project");
        return;
      }
      this.process = new MostlySilentColoredProcessHandler(command);
      daemonApi = new DaemonApi(process);
      daemonApi.listen(process, new DevToolsServiceListener());
      daemonApi.devToolsServe().thenAccept((DaemonApi.DevToolsAddress address) -> {
        if (!project.isOpen()) {
          // We should skip starting DevTools (and doing any UI work) if the project has been closed.
          return;
        }
        if (address == null) {
          logExceptionAndComplete("DevTools address was null");
        }
        else {
          devToolsFutureRef.get().complete(new DevToolsInstance(address.host, address.port));
        }
      });
    }
    catch (ExecutionException e) {
      logExceptionAndComplete(e);
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

  private void setUpWithPub() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      logExceptionAndComplete("Flutter SDK is null");
      return;
    }

    pubActivateDevTools(sdk).thenAccept(success -> {
      if (success) {
        pubRunDevTools(sdk);
      } else {
        logExceptionAndComplete("pub activate of DevTools failed");
      }
    });
  }

  private void pubRunDevTools(FlutterSdk sdk) {
    final FlutterCommand command = sdk.flutterPub(null, "global", "run", "devtools", "--machine", "--port=0");

    final ColoredProcessHandler handler = command.startProcessOrShowError(project);
    if (handler == null) {
      logExceptionAndComplete("Handler was null for pub global run command");
      return;
    }

    handler.addProcessListener(new ProcessAdapter() {
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
              devToolsFutureRef.get().complete(new DevToolsInstance(host, port));
            }
            else {
              logExceptionAndComplete("DevTools port was invalid");
              handler.destroyProcess();
            }
          }
          catch (JsonSyntaxException e) {
            logExceptionAndComplete(e);
            handler.destroyProcess();
          }
        }
      }
    });

    handler.startNotify();

    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        devToolsFutureRef.set(null);
        handler.destroyProcess();
      }
    });
  }

  private CompletableFuture<Boolean> pubActivateDevTools(FlutterSdk sdk) {
    final FlutterCommand command = sdk.flutterPub(null, "global", "activate", "devtools");

    final CompletableFuture<Boolean> result = new CompletableFuture<>();

    final Process process = command.start((ProcessOutput output) -> {
      if (output.getExitCode() != 0) {
        final String message = (output.getStdout() + "\n" + output.getStderr()).trim();
        FlutterConsoles.displayMessage(project, null, message, true);
      }
    }, null);

    try {
      final int resultCode = process.waitFor();
      result.complete(resultCode == 0);
    }
    catch (RuntimeException | InterruptedException re) {
      if (!result.isDone()) {
        result.complete(false);
      }
    }

    return result;
  }

  private void logExceptionAndComplete(String message) {
    logExceptionAndComplete(new Exception(message));
  }

  private void logExceptionAndComplete(Exception exception) {
    LOG.error(exception);
    FlutterInitializer.getAnalytics().sendException(StringUtil.getThrowableText(exception), false);
    final CompletableFuture<DevToolsInstance> future = devToolsFutureRef.get();
    if (future != null) {
      future.completeExceptionally(exception);
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

  private static GeneralCommandLine createCommand(String workDir, String command, ImmutableList<String> arguments) {
    final GeneralCommandLine result = new GeneralCommandLine().withWorkDirectory(workDir);
    result.setCharset(CharsetToolkit.UTF8_CHARSET);
    result.setExePath(FileUtil.toSystemDependentName(command));
    result.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());

    for (String argument : arguments) {
      result.addParameter(argument);
    }

    return result;
  }
}


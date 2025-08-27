/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import io.flutter.console.FlutterConsoles;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class DevToolsService {
  private static final @NotNull Logger LOG = Logger.getInstance(DevToolsService.class);

  protected static class DevToolsServiceListener implements DaemonEvent.Listener {
  }

  @NotNull private final Project project;

  @Nullable private DevToolsServerTask devToolsServerTask;

  @Nullable private BackgroundableProcessIndicator devToolsServerProgressIndicator;

  @NotNull private AtomicReference<CompletableFuture<DevToolsInstance>> devToolsFutureRef = new AtomicReference<>(null);

  @NotNull
  public static DevToolsService getInstance(@NotNull final Project project) {
    return Objects.requireNonNull(project.getService(DevToolsService.class));
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
      }
      else {
        return future;
      }
    }) == null) {
      devToolsFutureRef.set(new CompletableFuture<>());
      startServer();
    }

    return devToolsFutureRef.get();
  }

  public CompletableFuture<DevToolsInstance> getDevToolsInstanceWithForcedRestart() {
    final CompletableFuture<DevToolsInstance> futureInstance = devToolsFutureRef.updateAndGet((future) -> {
      if (future.isCompletedExceptionally() || future.isCancelled()) {
        return null;
      }
      else {
        return future;
      }
    });

    if (futureInstance == null) {
      devToolsFutureRef.set(new CompletableFuture<>());
      startServer(true);
    }
    else if (!futureInstance.isDone()) {
      futureInstance.cancel(true);
      devToolsFutureRef.set(new CompletableFuture<>());
      startServer(true);
    }

    return devToolsFutureRef.get();
  }

  private void startServer() {
    startServer(false);
  }

  private void startServer(boolean forceRestart) {
    if (forceRestart) {
      // If this is a force-restart request and the previous DevTools server is still running, cancel it before starting another.
      if (devToolsServerProgressIndicator != null && devToolsServerProgressIndicator.isRunning()) {
        devToolsServerProgressIndicator.cancel();
      }
    }
    else {
      // If this is not a force-restart request, do not start a new DevTools server if one is already running, or if we have a
      // DevTools instance.
      if (devToolsServerProgressIndicator != null) {
        if (devToolsServerProgressIndicator.isRunning() || devToolsInstanceExists()) {
          return;
        }
        else {
          devToolsServerProgressIndicator.cancel();
        }
      }
    }

    // Start the DevTools server.
    devToolsServerTask = new DevToolsServerTask(project, "Starting DevTools", devToolsFutureRef);
    devToolsServerProgressIndicator = new BackgroundableProcessIndicator(project, devToolsServerTask);
    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(
        devToolsServerTask, devToolsServerProgressIndicator);
  }

  private boolean devToolsInstanceExists() {
    final CompletableFuture<DevToolsInstance> devToolsFuture = devToolsFutureRef.get();
    return devToolsFuture != null && devToolsFuture.isDone() && !devToolsFuture.isCompletedExceptionally();
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
    LOG.info(exception);
    final CompletableFuture<DevToolsInstance> future = devToolsFutureRef.get();
    if (future != null) {
      future.completeExceptionally(exception);
    }
  }
}



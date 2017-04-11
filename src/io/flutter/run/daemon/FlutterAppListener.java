/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.base.Stopwatch;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listens for events while running or debugging an app.
 */
class FlutterAppListener implements DaemonEvent.Listener {
  private final @NotNull FlutterApp app;
  private final @NotNull ProgressHelper progress;

  private final AtomicReference<Stopwatch> stopwatch = new AtomicReference<>();

  FlutterAppListener(@NotNull FlutterApp app, @NotNull Project project) {
    this.app = app;
    this.progress = new ProgressHelper(project);
  }

  // process lifecycle

  @Override
  public void processWillTerminate() {
    progress.cancel();
    // Shutdown must be sync so that we prevent the processTerminated() event from being delivered
    // until a graceful shutdown has been tried.
    try {
      app.shutdownAsync().get();
    }
    catch (Exception e) {
      LOG.warn("exception while shutting down Flutter App", e);
    }
  }

  @Override
  public void processTerminated() {
    progress.cancel();
    app.changeState(FlutterApp.State.TERMINATED);
  }

  // daemon domain

  @Override
  public void onDaemonLogMessage(@NotNull DaemonEvent.LogMessage message) {
    LOG.info("flutter app: " + message.message);
  }

  // app domain

  @Override
  public void onAppStarting(DaemonEvent.AppStarting event) {
    app.setAppId(event.appId);
  }

  @Override
  public void onAppDebugPort(@NotNull DaemonEvent.AppDebugPort port) {
    app.setWsUrl(port.wsUri);

    String uri = port.baseUri;
    if (uri == null) return;

    if (uri.startsWith("file:")) {
      // Convert the file: url to a path.
      try {
        uri = new URL(uri).getPath();
        if (uri.endsWith(File.separator)) {
          uri = uri.substring(0, uri.length() - 1);
        }
      }
      catch (MalformedURLException e) {
        // ignore
      }
    }
    app.setBaseUri(uri);
  }

  @Override
  public void onAppStarted(DaemonEvent.AppStarted started) {
    app.changeState(FlutterApp.State.STARTED);
  }

  @Override
  public void onAppLog(@NotNull DaemonEvent.AppLog message) {
    final ConsoleView console = app.getConsole();
    if (console == null) return;
    console.print(message.log + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  @Override
  public void onAppProgressStarting(@NotNull DaemonEvent.AppProgress event) {
    progress.start(event.message);
    if (event.getType().startsWith("hot.")) {
      stopwatch.set(Stopwatch.createStarted());
    }
  }

  @Override
  public void onAppProgressFinished(@NotNull DaemonEvent.AppProgress event) {
    progress.done();
    final Stopwatch watch = stopwatch.getAndSet(null);
    if (watch != null) {
      watch.stop();
      switch (event.getType()) {
        case "hot.reload":
          reportElapsed(watch, "Reloaded", "reload");
          break;
        case "hot.restart":
          reportElapsed(watch, "Restarted", "restart");
          break;
      }
    }
  }

  private void reportElapsed(@NotNull Stopwatch watch, String verb, String analyticsName) {
    final long elapsedMs = watch.elapsed(TimeUnit.MILLISECONDS);
    FlutterInitializer.getAnalytics().sendTiming("run", analyticsName, elapsedMs);
  }

  @Override
  public void onAppStopped(@NotNull DaemonEvent.AppStopped stopped) {
    progress.cancel();
    app.getProcessHandler().destroyProcess();
  }

  private static final Logger LOG = Logger.getInstance(FlutterAppListener.class.getName());
}

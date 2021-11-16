/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

// RunContentManager

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.EventStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FlutterAppManager implements Disposable {
  public static FlutterAppManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterAppManager.class);
  }

  @NotNull
  private final Project project;

  private final EventStream<FlutterApp> activeAppSteam = new EventStream<>();

  private final ScheduledFuture task;
  private FlutterApp lastActiveApp;

  private FlutterAppManager(@NotNull Project project) {
    this.project = project;

    Disposer.register(project, this);

    task = JobScheduler.getScheduler().scheduleWithFixedDelay(
      this::updateActiveApp, 1, 1, TimeUnit.SECONDS);
  }

  @Override
  public void dispose() {
    task.cancel(false);
  }

  /**
   * Return the active (front most in the UI) Flutter app.
   * <p>
   * This could return null even if there is a running Flutter app, if the front-most
   * running process (focused in the Run or Debug view) is not a Flutter app instance.
   */
  @Nullable
  public FlutterApp getActiveApp() {
    final RunContentManager mgr = getRunContentManager();
    if (mgr == null) {
      return null;
    }
    final RunContentDescriptor descriptor = mgr.getSelectedContent();
    if (descriptor == null) {
      return null;
    }

    final ProcessHandler process = descriptor.getProcessHandler();
    if (process == null) {
      return null;
    }

    final FlutterApp app = FlutterApp.fromProcess(process);
    return app != null && app.isConnected() ? app : null;
  }

  public EventStream<FlutterApp> getActiveAppAsStream() {
    return activeAppSteam;
  }

  /**
   * Return the list of all running Flutter apps.
   */
  public List<FlutterApp> getApps() {
    final List<FlutterApp> apps = new ArrayList<>();
    final RunContentManager mgr = getRunContentManager();
    if (mgr == null) {
      return apps;
    }

    final List<RunContentDescriptor> runningProcesses = mgr.getAllDescriptors();
    for (RunContentDescriptor descriptor : runningProcesses) {
      final ProcessHandler process = descriptor.getProcessHandler();
      if (process != null) {
        final FlutterApp app = FlutterApp.fromProcess(process);
        if (app != null && app.isConnected()) {
          apps.add(app);
        }
      }
    }

    return apps;
  }

  private void updateActiveApp() {
    final FlutterApp activeApp = getActiveApp();

    if (activeApp != lastActiveApp) {
      lastActiveApp = activeApp;
      activeAppSteam.setValue(activeApp);
    }
  }

  @Nullable
  private RunContentManager getRunContentManager() {
    // Creating a RunContentManager causes a blank window to appear, so don't create it here.
    // See https://github.com/flutter/flutter-intellij/issues/4217
    if (ServiceManager.getServiceIfCreated(project, RunContentManager.class) == null) {
      return null;
    }
    return ExecutionManager.getInstance(project).getContentManager();
  }
}

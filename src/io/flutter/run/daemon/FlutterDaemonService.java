/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.flutter.sdk.FlutterSdkManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-lived singleton that communicates with external Flutter processes.
 *
 * <p>For each IntelliJ project there is one process used to poll for devices
 * (and populate the device menu) and any number of processes for running Flutter apps
 * (created by run or debug configurations).
 */
public class FlutterDaemonService {

  /**
   * Processes started to run or debug a Flutter application.
   *
   * <p>Access should be synchronized.
   */
  private final List<FlutterDaemonController> myAppControllers = new ArrayList<>();

  @NotNull
  public static FlutterDaemonService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterDaemonService.class);
  }

  private FlutterDaemonService(Project project) {
    Disposer.register(project, this::stopAppControllers);

    // Watch for Flutter SDK changes.
    final FlutterSdkManager.Listener sdkListener = new FlutterSdkManager.Listener() {

      @Override
      public void flutterSdkRemoved() {
        stopAppControllers();
      }
    };
    FlutterSdkManager.getInstance().addListener(sdkListener);
    Disposer.register(project, () -> FlutterSdkManager.getInstance().removeListener(sdkListener));
  }

  /**
   * Start a Flutter app.
   *
   * @param project    The Project
   * @param projectDir The path to the root directory of the Flutter project
   * @param deviceId   The device id as reported by the Flutter daemon
   * @param mode       The RunMode to use (release, debug, profile)
   */
  public FlutterApp startApp(@NotNull Project project,
                             @NotNull String projectDir,
                             @Nullable String deviceId,
                             @NotNull RunMode mode,
                             @Nullable String relativePath)
    throws ExecutionException {
    final boolean startPaused = mode == RunMode.DEBUG;
    final boolean isHot = mode.isReloadEnabled();

    final FlutterDaemonController controller = createEmptyAppController();
    final FlutterApp app = controller.startRunnerProcess(project, projectDir, deviceId, mode, startPaused, isHot, relativePath);

    app.addStateListener(newState -> {
      if (newState == FlutterApp.State.TERMINATED) {
        controller.forceExit();
      }
    });

    return app;
  }

  public FlutterApp startBazelApp(@NotNull Project project,
                                  @NotNull String projectDir,
                                  @NotNull String launchingScript,
                                  @Nullable FlutterDevice device,
                                  @NotNull RunMode mode,
                                  @NotNull String bazelTarget,
                                  @Nullable String additionalArguments)
    throws ExecutionException {
    final boolean startPaused = mode == RunMode.DEBUG;
    final boolean isHot = mode.isReloadEnabled();

    final FlutterDaemonController controller = createEmptyAppController();
    final FlutterApp app = controller.startBazelProcess(
      project, projectDir, device, mode, startPaused, isHot, launchingScript, bazelTarget, additionalArguments);

    app.addStateListener(newState -> {
      if (newState == FlutterApp.State.TERMINATED) {
        controller.forceExit();
      }
    });
    return app;
  }

  /**
   * Creates a new FlutterDaemonController that is registered with this daemon, but not started.
   */
  @NotNull FlutterDaemonController createEmptyAppController() {
    synchronized (myAppControllers) {
      final FlutterDaemonController controller = new FlutterDaemonController(this);
      myAppControllers.add(controller);
      controller.addProcessTerminatedListener(this::discard);
      return controller;
    }
  }


  private void discard(FlutterDaemonController controller) {
    synchronized (myAppControllers) {
      myAppControllers.remove(controller);
      controller.forceExit();
    }
  }

  private void stopAppControllers() {
    synchronized (myAppControllers) {
      for (FlutterDaemonController controller : myAppControllers) {
        controller.forceExit();
      }
      myAppControllers.clear();
    }
  }

  private static final Logger LOG = Logger.getInstance(FlutterDaemonService.class.getName());
}

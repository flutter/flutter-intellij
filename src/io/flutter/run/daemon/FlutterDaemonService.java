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
import com.intellij.util.containers.SortedList;
import gnu.trove.THashSet;
import io.flutter.FlutterMessages;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdkManager;
import io.flutter.utils.Refreshable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Long-lived singleton that communicates with external Flutter processes.
 *
 * <p>For each IntelliJ project there is one process used to poll for devices
 * (and populate the device menu) and any number of processes for running Flutter apps
 * (created by run or debug configurations).
 */
public class FlutterDaemonService {
  private final Project project;

  /**
   * The process used to watch for devices changes (for the device menu). May be null if not running.
   */
  private final Refreshable<DeviceDaemon> myDeviceDaemon = new Refreshable<>(DeviceDaemon::shutdown);

  /**
   * Processes started to run or debug a Flutter application.
   *
   * <p>Access should be synchronized.
   */
  private final List<FlutterDaemonController> myAppControllers = new ArrayList<>();

  private final Set<FlutterDevice> myConnectedDevices = new THashSet<>();
  private FlutterDevice mySelectedDevice;

  private final List<DeviceListener> myDeviceListeners = new ArrayList<>();

  public interface DeviceListener {
    void deviceAdded(FlutterDevice device);

    void selectedDeviceChanged(FlutterDevice device);

    void deviceRemoved(FlutterDevice device);
  }

  @NotNull
  public static FlutterDaemonService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterDaemonService.class);
  }

  private FlutterDaemonService(Project project) {
    this.project = project;
    Disposer.register(project, this::stopAppControllers);

    myDeviceDaemon.setDisposeParent(project);
    refreshDeviceDaemon();

    // Watch for Flutter SDK changes.
    final FlutterSdkManager.Listener sdkListener = new FlutterSdkManager.Listener() {
      @Override
      public void flutterSdkAdded() {
        refreshDeviceDaemon();
      }

      @Override
      public void flutterSdkRemoved() {
        stopAppControllers();
        refreshDeviceDaemon();
      }
    };
    FlutterSdkManager.getInstance().addListener(sdkListener);
    Disposer.register(project, () -> FlutterSdkManager.getInstance().removeListener(sdkListener));

    // Watch for Bazel workspace changes.
    WorkspaceCache.getInstance(project).subscribe(this::refreshDeviceDaemon);
  }

  /**
   * Runs a callback after the device daemon starts or stops.
   */
  public void addDeviceDaemonListener(Runnable callback) {
    myDeviceDaemon.subscribe(callback);
  }

  public void addDeviceListener(@NotNull DeviceListener listener) {
    myDeviceListeners.add(listener);
  }

  public void removeDeviceListener(@NotNull DeviceListener listener) {
    myDeviceListeners.remove(listener);
  }

  /**
   * Returns true if the device daemon is running.
   *
   * <p>(That is, we are watching for changes to available devices.)
   */
  public boolean isActive() {
    final DeviceDaemon daemon = myDeviceDaemon.getNow();
    return daemon != null && daemon.isRunning();
  }

  /**
   * Return the list of currently connected devices. The list is sorted by device name.
   *
   * @return List of ConnectedDevice
   */
  public List<FlutterDevice> getConnectedDevices() {
    final SortedList<FlutterDevice> list = new SortedList<>(Comparator.comparing(FlutterDevice::deviceName));
    list.addAll(myConnectedDevices);
    return list;
  }

  /**
   * @return the currently selected device
   */
  @Nullable
  public FlutterDevice getSelectedDevice() {
    return mySelectedDevice;
  }

  public boolean hasSelectedDevice() {
    return mySelectedDevice != null;
  }

  /**
   * Set the current selected device.
   */
  public void setSelectedDevice(@Nullable FlutterDevice device) {
    mySelectedDevice = device;

    for (DeviceListener listener : myDeviceListeners) {
      listener.selectedDeviceChanged(device);
    }
  }

  void addConnectedDevice(@NotNull FlutterDevice device) {
    myConnectedDevices.add(device);

    if (mySelectedDevice == null) {
      setSelectedDevice(device);
    }

    for (DeviceListener listener : myDeviceListeners) {
      // Called from background thread that's holding a lock.
      // Avoid deadlock by delivering events later.
      // (Events may not be delivered for a while due to a dialog.)
      SwingUtilities.invokeLater(() -> listener.deviceAdded(device));
    }
  }

  void removeConnectedDevice(@NotNull FlutterDevice device) {
    myConnectedDevices.remove(device);

    if (mySelectedDevice == device) {
      if (myConnectedDevices.isEmpty()) {
        setSelectedDevice(null);
      }
      else {
        setSelectedDevice(getConnectedDevices().get(0));
      }
    }

    for (DeviceListener listener : myDeviceListeners) {
      // Called from background thread that's holding a lock.
      // Avoid deadlock by delivering events later.
      SwingUtilities.invokeLater(() -> listener.deviceRemoved(device));
    }
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

  /**
   * Updates the device daemon to what it should be based on current configuation.
   *
   * <p>This might mean starting it, stopping it, or restarting it.
   */
  void refreshDeviceDaemon() {
    myDeviceDaemon.refresh(this::chooseNextDaemon);
  }

  /**
   * Returns the device daemon that should be running.
   *
   * <p>Starts it if needed. If null is returned then the previous daemon will be shut down.
   */
  private DeviceDaemon chooseNextDaemon() {
    final DeviceDaemon.Command nextCommand = DeviceDaemon.chooseCommand(project);
    if (nextCommand == null) {
      return null; // Unconfigured; shut down if running.
    }

    final DeviceDaemon previous = myDeviceDaemon.getNow();
    if (previous != null && !previous.needRestart(nextCommand)) {
      return previous; // Don't do anything; current daemon is what we want.
    }

    try {
      return nextCommand.start(this);
    }
    catch (ExecutionException e) {
      FlutterMessages.showError("Unable to start process to watch Flutter devices", e.toString());
      return previous; // Couldn't start a new one so don't shut it down.
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

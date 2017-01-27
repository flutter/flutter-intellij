/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.SortedList;
import gnu.trove.THashSet;
import io.flutter.FlutterMessages;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkManager;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Long lived singleton that communicates with controllers attached to external Flutter processes.
 * <p>
 * There is one Daemon service per IntelliJ project.
 */
public class FlutterDaemonService {
  private final Object myLock = new Object();

  private final List<FlutterDaemonController> myControllers = new ArrayList<>();
  private FlutterDaemonController myPollingController;

  private final Set<FlutterDevice> myConnectedDevices = new THashSet<>();
  private FlutterDevice mySelectedDevice;

  private final FlutterSdkManager.Listener mySdkListener = new SdkListener();
  private final List<DeviceListener> myDeviceListeners = new ArrayList<>();
  private final DaemonListener myDiscardListener = new DaemonListener() {
    @Override
    public void daemonInput(String json, FlutterDaemonController controller) {
    }

    @Override
    public void aboutToTerminate(ProcessHandler handler, FlutterDaemonController controller) {
    }

    @Override
    public void processTerminated(ProcessHandler handler, FlutterDaemonController controller) {
      discard(controller);
    }
  };

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
    listenForSdkChanges();
    if (FlutterSdk.getFlutterSdk(project) != null) {
      schedulePolling();
    }
    Disposer.register(project, this::stopControllers);
    Disposer.register(project, this::stopListeningForSdkChanges);
  }

  private class SdkListener implements FlutterSdkManager.Listener {
    @Override
    public void flutterSdkAdded() {
      schedulePolling();
    }

    @Override
    public void flutterSdkRemoved() {
      stopControllers();
    }
  }

  private void listenForSdkChanges() {
    FlutterSdkManager.getInstance().addListener(mySdkListener);
  }

  private void stopListeningForSdkChanges() {
    FlutterSdkManager.getInstance().removeListener(mySdkListener);
  }

  public void addDeviceListener(@NotNull DeviceListener listener) {
    myDeviceListeners.add(listener);
  }

  public void removeDeviceListener(@NotNull DeviceListener listener) {
    myDeviceListeners.remove(listener);
  }

  /**
   * Return whether the daemon service is up and running.
   */
  public boolean isActive() {
    return myPollingController != null;
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
      listener.deviceAdded(device);
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
      listener.deviceRemoved(device);
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

    final FlutterDaemonController controller = createController();
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

    final FlutterDaemonController controller = createController();
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
   * Create a new FlutterDaemonController.
   *
   * @return A FlutterDaemonController that can be used to start the app in the project directory
   */
  @NotNull
  private FlutterDaemonController createController() {
    synchronized (myLock) {
      final FlutterDaemonController controller = new FlutterDaemonController(this);
      myControllers.add(controller);
      controller.addDaemonListener(myDiscardListener);
      return controller;
    }
  }

  void schedulePolling() {
    if (!FlutterSdkUtil.hasFlutterModules()) return;

    synchronized (myLock) {
      if (myPollingController != null &&
          myPollingController.getProcessHandler() != null &&
          !myPollingController.getProcessHandler().isProcessTerminating()) {
        return;
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      synchronized (myLock) {
        myPollingController = new FlutterDaemonController(this);
        myControllers.add(myPollingController);
        myPollingController.addDaemonListener(myDiscardListener);
      }

      try {
        myPollingController.startDevicePoller();
      }
      catch (ExecutionException error) {
        FlutterMessages.showError("Unable to poll for Flutter devices", error.toString());
      }
    });
  }

  private void discard(FlutterDaemonController controller) {
    synchronized (myLock) {
      myControllers.remove(controller);
      controller.forceExit();
    }
  }

  private void stopControllers() {
    synchronized (myLock) {
      for (FlutterDaemonController controller : myControllers) {
        controller.forceExit();
      }
      myControllers.clear();
      myPollingController = null;
    }
  }
}

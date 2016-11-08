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
 */
public class FlutterDaemonService {
  private static final boolean HOT_MODE_DEFAULT = true;
  private static final boolean HOT_MODE_RELEASE = false;

  private final Object myLock = new Object();
  private List<FlutterDaemonController> myControllers = new ArrayList<>();
  private FlutterDaemonController myPollster;
  private Set<ConnectedDevice> myConnectedDevices = new THashSet<>();
  private final SdkListener mySdkListener = new SdkListener();
  private ConnectedDevice mySelectedDevice;
  private FlutterAppManager myManager = new FlutterAppManager(this);
  private List<DeviceListener> myDeviceListeners = new ArrayList<>();

  private DaemonListener myListener = new DaemonListener() {
    public void daemonInput(String string, FlutterDaemonController controller) {
      FlutterAppManager mgr;
      synchronized (myLock) {
        mgr = myManager;
      }
      mgr.processInput(string, controller);
    }

    public void enableDevicePolling(FlutterDaemonController controller) {
      FlutterAppManager mgr;
      synchronized (myLock) {
        mgr = myManager;
      }
      mgr.enableDevicePolling(controller);
    }

    @Override
    public void aboutToTerminate(ProcessHandler handler, FlutterDaemonController controller) {
      assert handler == controller.getProcessHandler();
      FlutterAppManager mgr;
      synchronized (myLock) {
        mgr = myManager;
      }
      mgr.aboutToTerminateAll(controller);
    }

    @Override
    public void processTerminated(ProcessHandler handler, FlutterDaemonController controller) {
      if (handler == controller.getProcessHandler()) {
        discard(controller);
      }
    }
  };

  public interface DeviceListener {
    void selectedDeviceChanged(ConnectedDevice device);
  }

  @Nullable
  public static FlutterDaemonService getInstance() {
    return ServiceManager.getService(FlutterDaemonService.class);
  }

  private FlutterDaemonService() {
    listenForSdkChanges();
    schedulePolling();
    Disposer.register(ApplicationManager.getApplication(), this::stopControllers);
    Disposer.register(ApplicationManager.getApplication(), this::stopListeningForSdkChanges);
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

  public void addDeviceListener(DeviceListener listener) {
    myDeviceListeners.add(listener);
  }

  public void removeDeviceListener(DeviceListener listener) {
    myDeviceListeners.remove(listener);
  }

  /**
   * Return the list of currently connected devices. The list is sorted by device name.
   *
   * @return List of ConnectedDevice
   */
  public List<ConnectedDevice> getConnectedDevices() {
    SortedList<ConnectedDevice> list = new SortedList<>(Comparator.comparing(ConnectedDevice::deviceName));
    list.addAll(myConnectedDevices);
    return list;
  }

  /**
   * @return the currently selected device
   */
  @Nullable
  public ConnectedDevice getSelectedDevice() {
    return mySelectedDevice;
  }

  /**
   * Set the current selected device.
   */
  public void setSelectedDevice(@Nullable ConnectedDevice device) {
    mySelectedDevice = device;

    for (DeviceListener listener : myDeviceListeners) {
      listener.selectedDeviceChanged(device);
    }
  }

  void addConnectedDevice(ConnectedDevice device) {
    myConnectedDevices.add(device);

    if (mySelectedDevice == null) {
      setSelectedDevice(device);
    }
  }

  void removeConnectedDevice(ConnectedDevice device) {
    myConnectedDevices.remove(device);

    if (mySelectedDevice == device) {
      if (myConnectedDevices.isEmpty()) {
        setSelectedDevice(null);
      }
      else {
        setSelectedDevice(getConnectedDevices().get(0));
      }
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
                             @NotNull String deviceId,
                             @NotNull RunMode mode,
                             boolean isHot,
                             @Nullable String relativePath)
    throws ExecutionException {
    boolean isPaused = mode.isDebug();
    FlutterDaemonController controller = controllerFor(projectDir, deviceId);
    if (controller.getProcessHandler() == null || controller.getProcessHandler().isProcessTerminated()) {
      controller.forkProcess(project);
    }
    FlutterAppManager mgr;
    synchronized (myLock) {
      mgr = myManager;
    }
    return mgr.startApp(controller, deviceId, mode, project, isPaused, isHot, relativePath);
  }

  /**
   * Scan the list of controllers to see if an existing controller can be reused. If not, create a new one.
   * Controllers can be reused if the directory is the same. If a controller is found that matches directory
   * and device then terminate the app on that device and select that controller.
   * NB: Currently controllers are not shared due to limitations of the debugger.
   *
   * @param projectDir The path to the project root directory
   * @param deviceId   The device id reported by the daemon
   * @return A FlutterDaemonController that can be used to start the app in the project directory
   */
  @NotNull
  private FlutterDaemonController controllerFor(String projectDir, String deviceId) {
    synchronized (myLock) {
      //for (FlutterDaemonController controller : myControllers) {
      //  if (controller.isForProject(projectDir)) {
      //    controller.setProjectAndDevice(projectDir, deviceId);
      //    controller.addListener(myListener);
      //    return controller;
      //  }
      //}
      FlutterDaemonController newController = new FlutterDaemonController(projectDir);
      myControllers.add(newController);
      newController.addListener(myListener);
      return newController;
    }
  }

  void schedulePolling() {
    if (!FlutterSdkUtil.isFluttering()) return;
    synchronized (myLock) {
      if (myPollster != null && myPollster.getProcessHandler() != null && !myPollster.getProcessHandler().isProcessTerminating()) {
        return;
      }
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      synchronized (myLock) {
        myPollster = new FlutterDaemonController(null);
        myPollster.addListener(myListener);
        myControllers.add(myPollster);
        myManager.startDevicePoller(myPollster);
      }
    });
  }

  private void discard(FlutterDaemonController controller) {
    synchronized (myLock) {
      if (controller == myPollster) {
        controller.setProjectAndDevice(null, null);
      }
      else {
        myControllers.remove(controller);
        controller.removeListener(myListener);
        controller.forceExit();
      }
    }
  }

  private void stopControllers() {
    synchronized (myLock) {
      for (FlutterDaemonController controller : myControllers) {
        controller.removeListener(myListener);
        controller.forceExit();
      }
      myControllers.clear();
      myPollster = null;
    }
  }
}

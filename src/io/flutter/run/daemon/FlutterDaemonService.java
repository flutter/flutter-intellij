/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.gson.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.SortedList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Long lived singleton that communicates with controllers attached to external Flutter processes.
 */
public class FlutterDaemonService {
  private static final Logger LOG = Logger.getInstance("#io.flutter.run.daemon.FlutterDaemonService");
  private static final boolean HOT_MODE_DEFAULT = false;
  private static final String TARGET_DEFAULT = null;
  private static final String ROUTE_DEFAULT = null;

  private final Object myLock = new Object(); // TODO Determine if synchronization is really needed
  private List<FlutterDaemonController> myControllers = new ArrayList<>();
  private FlutterDaemonController myPollster;
  private Set<ConnectedDevice> myConnectedDevices = new THashSet<>();
  private FlutterAppManager myManager = new FlutterAppManager();

  private DaemonListener myListener = (json, controller) -> {
    try {
      JsonParser jp = new JsonParser();
      JsonElement elem = jp.parse(json);
      JsonObject obj = elem.getAsJsonObject();
      JsonPrimitive primId = obj.getAsJsonPrimitive("id");
      if (primId == null) {
        myManager.handleEvent(obj, controller, json);
      }
      else {
        myManager.handleResponse(primId.getAsInt(), obj, controller);
      }
    }
    catch (JsonSyntaxException ex) {
      LOG.error(ex);
    }
  };

  @Nullable
  public static FlutterDaemonService getInstance() {
    return ServiceManager.getService(FlutterDaemonService.class);
  }

  public FlutterDaemonService() {
    Disposer.register(ApplicationManager.getApplication(), this::stopControllers);
    schedulePolling();
  }

  /**
   * Return the list of currently connected devices. The list is sorted by device name.
   * TODO(pq,messick) Extend the debugger UI to allow selecting a device from this list.
   *
   * @return List of ConnectedDevice
   */
  public Collection<ConnectedDevice> getConnectedDevices() {
    SortedList<ConnectedDevice> list = new SortedList<>((o1, o2) -> o1.deviceName().compareTo(o2.deviceName()));
    list.addAll(myConnectedDevices);
    return list;
  }

  public void addConnectedDevice(ConnectedDevice device) {
    myConnectedDevices.add(device);
  }

  /**
   * Start a Flutter app.
   *
   * @param projectDir The path to the root directory of the Flutter project
   * @param deviceId   The device id as reported by the Flutter daemon
   * @param mode       The RunMode to use (release, debug, profile)
   */
  public FlutterApp startApp(@NotNull String projectDir, @NotNull String deviceId, @NotNull RunMode mode) {
    boolean isPaused = mode.isDebug();
    FlutterDaemonController controller = controllerFor(projectDir, deviceId);
    return myManager.startApp(this, controller, deviceId, mode, isPaused, HOT_MODE_DEFAULT, TARGET_DEFAULT, ROUTE_DEFAULT);
  }

  /**
   * Scan the list of controllers to see if an existing controller can be reused. If not, create a new one.
   * Controllers can be reused if the directory is the same. If a controller is found that matches directory
   * and device then terminate the app on that device and select that controller.
   *
   * @param projectDir The path to the project root directory
   * @param deviceId   The device id reported by the daemon
   * @return A FlutterDaemonController that can be used to start the app in the project directory
   */
  @NotNull
  private FlutterDaemonController controllerFor(String projectDir, String deviceId) {
    synchronized (myLock) {
      for (FlutterDaemonController controller : myControllers) {
        if (controller.isForProject(projectDir)) {
          controller.setProjectAndDevice(projectDir, deviceId);
          return controller;
        }
      }
      FlutterDaemonController newController = new FlutterDaemonController(projectDir);
      myControllers.add(newController);
      return newController;
    }
  }

  void schedulePolling() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      synchronized (myLock) {
        myPollster = new FlutterDaemonController(null);
        myControllers.add(myPollster);
        // TODO enable polling in myPollster
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

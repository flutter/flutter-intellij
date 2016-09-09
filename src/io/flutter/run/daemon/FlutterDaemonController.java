/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import java.util.ArrayList;
import java.util.List;

/**
 * Control an external Flutter process, including reading events and responsesmfrom its stdout and
 * writing commands to its stdin.
 */
public class FlutterDaemonController {
  private String myProjectDirectory;
  private List<String> myDeviceIds = new ArrayList<>();
  private List<DaemonListener> myListeners = new ArrayList<>();

  public FlutterDaemonController(String projectDir) {
    myProjectDirectory = projectDir;
  }

  public void addListener(DaemonListener listener) {
    removeListener(listener);
    myListeners.add(listener);
  }

  public void removeListener(DaemonListener listener) {
    myListeners.remove(listener);
  }

  public void forceExit() {
    // TODO Stop all apps and terminate the external process
    myDeviceIds.clear();
    myListeners.clear();
    myProjectDirectory = null;
  }

  public String getProjectDirectory() {
    return myProjectDirectory;
  }

  /**
   * Return true if this controller can be used by the app in the projectDir.
   * The very first controller that is created (to do device discovery) has
   * no project directory so it can be reused. Always invoke #setProjectAndDevice
   * after selecting a controller to ensure both directory and device id are set.
   *
   * @param projectDir The path to a project directory
   * @return true if the instance can be used or reused
   */
  public boolean isForProject(String projectDir) {
    return myProjectDirectory == null || myProjectDirectory.equals(projectDir);
  }

  void setProjectAndDevice(String projectDir, String deviceId) {
    if (hasDeviceId(deviceId)) {
      removeDeviceId(deviceId);
    }
    myProjectDirectory = projectDir;
    if (deviceId != null) myDeviceIds.add(deviceId);
  }

  boolean hasDeviceId(String id) {
    return myDeviceIds.contains(id);
  }

  void removeDeviceId(String deviceId) {
    myDeviceIds.remove(deviceId);
  }
}

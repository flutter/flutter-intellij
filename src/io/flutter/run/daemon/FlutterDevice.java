/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import java.util.Objects;

public class FlutterDevice {
  private final String myDeviceName;
  private final String myDeviceId;
  private final String myPlatform;
  private final boolean myEmulator;

  FlutterDevice(String deviceName, String deviceId, String platform, boolean emulator) {
    myDeviceName = deviceName;
    myDeviceId = deviceId;
    myPlatform = platform;
    myEmulator = emulator;
  }

  public String deviceName() {
    return myDeviceName;
  }

  public String deviceId() {
    return myDeviceId;
  }

  public String platform() {
    return myPlatform;
  }

  public boolean emulator() {
    return myEmulator;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof FlutterDevice) {
      return Objects.equals(myDeviceName, ((FlutterDevice)other).deviceName()) &&
             Objects.equals(myDeviceId, ((FlutterDevice)other).deviceId()) &&
             Objects.equals(myPlatform, ((FlutterDevice)other).platform());
    }
    else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDeviceName, myDeviceId, myPlatform);
  }

  @Override
  public String toString() {
    return myDeviceName;
  }
}

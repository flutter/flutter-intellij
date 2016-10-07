/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.base.Objects;

public interface ConnectedDevice {

  /**
   * The name of the device, which can be shown to the user.
   *
   * @return The device name returned by the device
   */
  String deviceName();

  /**
   * @return The deviceId for a device or simulator
   */
  String deviceId();

  /**
   * @return The name of the platform of the device
   */
  String platform();

  /**
   * @return Whether the device is an emulator
   */
  boolean emulator();
}

class FlutterDevice implements ConnectedDevice {

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

  @Override
  public String deviceName() {
    return myDeviceName;
  }

  @Override
  public String deviceId() {
    return myDeviceId;
  }

  @Override
  public String platform() {
    return myPlatform;
  }

  @Override
  public boolean emulator() {
    return myEmulator;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof FlutterDevice) {
      return Objects.equal(myDeviceName, ((FlutterDevice)other).deviceName()) &&
             Objects.equal(myDeviceId, ((FlutterDevice)other).deviceId()) &&
             Objects.equal(myPlatform, ((FlutterDevice)other).platform());
    }
    else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myDeviceName, myDeviceId, myPlatform);
  }

  @Override
  public String toString() {
    return myDeviceName;
  }
}
/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

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
}

class FlutterDevice implements ConnectedDevice {

  private final String myDeviceName;
  private final String myDeviceId;
  private final String myPlatform;

  FlutterDevice(String deviceName, String deviceId, String platform) {
    myDeviceName = deviceName;
    myDeviceId = deviceId;
    myPlatform = platform;
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
}
/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import io.flutter.sdk.XcodeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public class FlutterDevice {
  private final @NotNull String myDeviceId;
  private final @NotNull String myDeviceName;
  private final @Nullable String myPlatform;
  private final boolean myEmulator;

  FlutterDevice(@NotNull String deviceId, @NotNull String deviceName, @Nullable String platform, boolean emulator) {
    myDeviceId = deviceId;
    myDeviceName = deviceName;
    myPlatform = platform;
    myEmulator = emulator;
  }

  @NotNull
  public String deviceId() {
    return myDeviceId;
  }

  @NotNull
  public String deviceName() {
    return myDeviceName;
  }

  @Nullable
  public String platform() {
    return myPlatform;
  }

  public boolean emulator() {
    return myEmulator;
  }

  public boolean isIOS() {
    return myPlatform != null && (myPlatform.equals("ios") || myPlatform.startsWith("darwin"));
  }

  @Override
  public boolean equals(Object other) {
    //noinspection SimplifiableIfStatement
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

  /**
   * Given a collection of devices, return a unique name for this device.
   */
  public String getUniqueName(Collection<FlutterDevice> devices) {
    for (final FlutterDevice other : devices) {
      if (other == this) {
        continue;
      }
      if (other.deviceName().equals(deviceName())) {
        return deviceName() + " (" + deviceId() + ")";
      }
    }
    return deviceName();
  }

  /**
   * Bring the window representing this device to the foreground. This is a no-op for
   * non-emulator, non-iOS devices.
   */
  public void bringToFront() {
    if (emulator() && isIOS()) {
      // Bring the iOS simulator to front.
      XcodeUtils.openSimulator();
    }
  }

  /**
   * Return the "flutter-tester" device.
   */
  public static FlutterDevice getTester() {
    return new FlutterDevice("flutter-tester", "Flutter test device", null, false);
  }
}

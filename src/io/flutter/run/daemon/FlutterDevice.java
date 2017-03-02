/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public @NotNull String deviceId() {
    return myDeviceId;
  }

  public @NotNull String deviceName() {
    return myDeviceName;
  }

  public @Nullable String platform() {
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
}

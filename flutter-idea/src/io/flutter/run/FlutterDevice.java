/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

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

  private final @Nullable String myCategory;
  private final @Nullable String myPlatformType;
  private final boolean myEphemeral;

  public FlutterDevice(@NotNull String deviceId, @NotNull String deviceName, @Nullable String platform, boolean emulator) {
    this(deviceId, deviceName, platform, emulator, null, null, null);
  }

  public FlutterDevice(
    @NotNull String deviceId, @NotNull String deviceName, @Nullable String platform,
    boolean emulator,
    @Nullable String category, @Nullable String platformType, @Nullable Boolean ephemeral
  ) {
    myDeviceId = deviceId;
    myDeviceName = deviceName.replaceAll("_", " ");
    myPlatform = platform;
    myEmulator = emulator;
    myCategory = category;
    myPlatformType = platformType;
    myEphemeral = ephemeral == null ? true : ephemeral;
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

  /**
   * One of 'web', 'mobile', or 'desktop'.
   */
  @Nullable
  public String category() {
    return myCategory;
  }

  @Nullable
  public String platformType() {
    return myPlatformType;
  }

  /**
   * Whether the device is persistent on the machine.
   * <p>
   * Web and desktop devices are generally non-ephemeral; mobile devices are generally ephemeral
   */
  public boolean ephemeral() {
    return myEphemeral;
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
      if (other.presentationName().equals(presentationName())) {
        return presentationName() + " (" + deviceId() + ")";
      }
    }
    return presentationName();
  }

  /**
   * Bring the window representing this device to the foreground. This is a no-op for
   * non-emulator, non-iOS devices.
   */
  public void bringToFront() {
    if (emulator() && isIOS()) {
      // Bring the iOS simulator to front.
      XcodeUtils.openSimulator(null);
    }
  }

  /**
   * Return the "flutter-tester" device.
   */
  public static FlutterDevice getTester() {
    return new FlutterDevice("flutter-tester", "Flutter test device", null, false);
  }

  public String presentationName() {
    if (category() != null) {
      return deviceName() + " (" + category() + ")";
    }
    else {
      return deviceName();
    }
  }
}

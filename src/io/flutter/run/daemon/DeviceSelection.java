/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An immutable snapshot of the list of connected devices and current selection.
 */
class DeviceSelection {
  static final DeviceSelection EMPTY = new DeviceSelection(ImmutableList.of(), null);

  @NotNull private final ImmutableList<FlutterDevice> devices;
  @Nullable private final FlutterDevice selection;

  private DeviceSelection(@NotNull ImmutableList<FlutterDevice> devices, @Nullable FlutterDevice selected) {
    this.devices = devices;
    this.selection = selected;
  }

  @NotNull ImmutableList<FlutterDevice> getDevices() {
    return devices;
  }

  @Nullable FlutterDevice getSelection() {
    return selection;
  }

  /**
   * Returns a new snapshot with the devices changed and the selection updated appropriately.
   */
  @NotNull
  DeviceSelection withDevices(@NotNull List<FlutterDevice> newDevices) {
    final String selectedId = selection == null ? null : selection.deviceId();
    final Optional<FlutterDevice> selectedDevice = findById(newDevices, selectedId);
    // If there is only one device, default to it.
    final FlutterDevice selectionOrDefault = selectedDevice.orElse(newDevices.size() == 1 ? newDevices.get(0) : null);
    return new DeviceSelection(ImmutableList.copyOf(newDevices), selectionOrDefault);
  }

  /**
   * Returns a new snapshot with the given device id selected, if possible.
   */
  @NotNull
  DeviceSelection withSelection(@Nullable String id) {
    return new DeviceSelection(devices, findById(devices, id).orElse(selection));
  }

  private static Optional<FlutterDevice> findById(@NotNull List<FlutterDevice> candidates, @Nullable String id) {
    if (id == null) return Optional.empty();
    return candidates.stream().filter((d) -> d.deviceId().equals(id)).findFirst();
  }
}

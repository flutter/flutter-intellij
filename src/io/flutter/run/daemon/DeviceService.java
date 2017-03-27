/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdkManager;
import io.flutter.utils.Refreshable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides the list of available devices (mobile phones or emulators) that appears in the dropdown menu.
 */
public class DeviceService {
  private final Project project;

  /**
   * The process used to watch for device list changes (for the device menu). May be null if not running.
   */
  private final Refreshable<DeviceDaemon> deviceDaemon = new Refreshable<>(DeviceDaemon::shutdown);

  private final AtomicReference<DeviceSelection> deviceSelection = new AtomicReference<>(DeviceSelection.EMPTY);

  private final AtomicReference<ImmutableSet<Runnable>> listeners = new AtomicReference<>(ImmutableSet.of());

  public static
  @NotNull
  DeviceService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DeviceService.class);
  }

  private DeviceService(@NotNull Project project) {
    this.project = project;

    deviceDaemon.setDisposeParent(project);
    deviceDaemon.subscribe(this::refreshDeviceSelection);
    refreshDeviceDaemon();

    // Watch for Flutter SDK changes.
    final FlutterSdkManager.Listener sdkListener = new FlutterSdkManager.Listener() {
      @Override
      public void flutterSdkAdded() {
        refreshDeviceDaemon();
      }

      @Override
      public void flutterSdkRemoved() {
        refreshDeviceDaemon();
      }
    };
    FlutterSdkManager.getInstance(project).addListener(sdkListener);
    Disposer.register(project, () -> FlutterSdkManager.getInstance(project).removeListener(sdkListener));

    // Watch for Bazel workspace changes.
    WorkspaceCache.getInstance(project).subscribe(this::refreshDeviceDaemon);
  }

  /**
   * Adds a callback for any changes to the status, device list, or selection.
   */
  public void addListener(@NotNull Runnable callback) {
    listeners.updateAndGet((old) -> {
      final List<Runnable> changed = new ArrayList<>();
      changed.addAll(old);
      changed.add(callback);
      return ImmutableSet.copyOf(changed);
    });
  }

  /**
   * Returns whether the device list is inactive, loading, or ready.
   */
  public State getStatus() {
    final DeviceDaemon daemon = deviceDaemon.getNow();
    if (daemon != null && daemon.isRunning()) {
      return State.READY;
    }
    else if (deviceDaemon.getState() == Refreshable.State.BUSY) {
      return State.LOADING;
    }
    else {
      return State.INACTIVE;
    }
  }

  /**
   * Returns the currently connected devices, sorted by device name.
   */
  public Collection<FlutterDevice> getConnectedDevices() {
    return deviceSelection.get().getDevices();
  }

  /**
   * Returns the currently selected device.
   * <p>
   * <p>When there is no device list (perhaps because the daemon isn't running), this will be null.
   */
  public
  @Nullable
  FlutterDevice getSelectedDevice() {
    return deviceSelection.get().getSelection();
  }

  public void setSelectedDevice(@Nullable FlutterDevice device) {
    deviceSelection.updateAndGet((old) -> old.withSelection(device == null ? null : device.deviceId()));
    fireChangeEvent();
  }

  private synchronized void refreshDeviceSelection() {
    deviceSelection.updateAndGet((old) -> {
      final DeviceDaemon daemon = deviceDaemon.getNow();
      final List<FlutterDevice> newDevices = daemon == null ? ImmutableList.of() : daemon.getDevices();
      return old.withDevices(newDevices);
    });
    fireChangeEvent();
  }

  private void fireChangeEvent() {
    SwingUtilities.invokeLater(() -> {
      if (project.isDisposed()) return;
      for (Runnable listener : listeners.get()) {
        try {
          listener.run();
        }
        catch (Exception e) {
          LOG.error("DeviceDaemon listerner threw an exception", e);
        }
      }
    });
  }

  /**
   * Updates the device daemon to what it should be based on current configuation.
   * <p>
   * <p>This might mean starting it, stopping it, or restarting it.
   */
  private void refreshDeviceDaemon() {
    if (project.isDisposed()) return;
    deviceDaemon.refresh(this::chooseNextDaemon);
  }

  /**
   * Returns the device daemon that should be running.
   * <p>
   * <p>Starts it if needed. If null is returned then the previous daemon will be shut down.
   */
  private DeviceDaemon chooseNextDaemon(Refreshable.Request<DeviceDaemon> request) {
    final DeviceDaemon.Command nextCommand = DeviceDaemon.chooseCommand(project);
    if (nextCommand == null) {
      return null; // Unconfigured; shut down if running.
    }

    final DeviceDaemon previous = request.getPrevious();
    if (previous != null && !previous.needRestart(nextCommand)) {
      return previous; // Don't do anything; current daemon is what we want.
    }

    // Wait a bit to see if we get cancelled.
    // This is to try to avoid starting a process only to immediately kill it.
    try {
      Thread.sleep(50);
    }
    catch (InterruptedException e) {
      return previous;
    }
    if (request.isCancelled()) {
      return previous;
    }

    try {
      return nextCommand.start(request::isCancelled, this::refreshDeviceSelection);
    }
    catch (ExecutionException e) {
      LOG.error("Unable to start process to watch Flutter devices", e);
      return previous; // Couldn't start a new one so don't shut it down.
    }
  }

  public enum State {INACTIVE, LOADING, READY}

  private static final Logger LOG = Logger.getInstance(DeviceService.class.getName());
}

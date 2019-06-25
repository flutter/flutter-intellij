/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.FlutterDevice;
import io.flutter.sdk.FlutterSdkManager;
import io.flutter.utils.Refreshable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// TODO(devoncarew): This class performs blocking work on the UI thread; we should fix.

/**
 * Provides the list of available devices (mobile phones or emulators) that appears in the dropdown menu.
 */
public class DeviceService {
  @NotNull private final Project project;

  /**
   * The process used to watch for device list changes (for the device menu). May be null if not running.
   */
  private final Refreshable<DeviceDaemon> deviceDaemon = new Refreshable<>(DeviceDaemon::shutdown);

  private final AtomicReference<DeviceSelection> deviceSelection = new AtomicReference<>(DeviceSelection.EMPTY);

  private final AtomicReference<ImmutableSet<Runnable>> listeners = new AtomicReference<>(ImmutableSet.of());

  private final AtomicLong lastRestartTime = new AtomicLong(0);

  @NotNull
  public static DeviceService getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, DeviceService.class);
  }

  private DeviceService(@NotNull final Project project) {
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

    // Watch for Java SDK changes. (Used to get the value of ANDROID_HOME.)
    ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener(this::refreshDeviceDaemon);
  }

  /**
   * Adds a callback for any changes to the status, device list, or selection.
   */
  public void addListener(@NotNull Runnable callback) {
    listeners.updateAndGet((old) -> {
      final List<Runnable> changed = new ArrayList<>(old);
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
  @Nullable
  public FlutterDevice getSelectedDevice() {
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
          FlutterUtils.warn(LOG, "DeviceDaemon listener threw an exception", e);
        }
      }
    });
  }

  /**
   * Updates the device daemon to what it should be based on current configuration.
   * <p>
   * <p>This might mean starting it, stopping it, or restarting it.
   */
  private void refreshDeviceDaemon() {
    if (project.isDisposed()) return;
    deviceDaemon.refresh(this::chooseNextDaemon);
  }

  private void daemonStopped(String details) {
    if (project.isDisposed()) return;

    final DeviceDaemon current = deviceDaemon.getNow();
    if (current == null || current.isRunning()) {
      // The active daemon didn't die, so it must be some older process. Just log it.
      LOG.info("A Flutter device daemon stopped.\n" + details);
      return;
    }

    // If we haven't tried restarting recently, try again.
    final long now = System.currentTimeMillis();
    final long millisSinceLastRestart = now - lastRestartTime.get();
    if (millisSinceLastRestart > TimeUnit.SECONDS.toMillis(20)) {
      LOG.info("A Flutter device daemon stopped. Automatically restarting it.\n" + details);
      refreshDeviceDaemon();
      lastRestartTime.set(now);
      return;
    }

    // Display as a notification to the user.
    final ApplicationInfo info = ApplicationInfo.getInstance();
    FlutterMessages.showWarning("Flutter daemon terminated", "Consider re-starting " + info.getVersionName() + ".");
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

    // Wait a bit to see if we get cancelled. This is to try to avoid starting a process only to
    // immediately kill it. Also, delay a bit in case the flutter tool just upgraded the sdk;
    // we'll need a bit more time to start up.
    try {
      Thread.sleep(100);
    }
    catch (InterruptedException e) {
      return previous;
    }
    if (request.isCancelled()) {
      return previous;
    }

    try {
      return nextCommand.start(request::isCancelled, this::refreshDeviceSelection, this::daemonStopped);
    }
    catch (ExecutionException e) {
      LOG.error(e);
      return previous; // Couldn't start a new one so don't shut it down.
    }
  }

  public void restart() {
    if (project.isDisposed()) return;

    JobScheduler.getScheduler().schedule(this::shutDown, 0, TimeUnit.SECONDS);
    JobScheduler.getScheduler().schedule(this::refreshDeviceDaemon, 4, TimeUnit.SECONDS);
  }

  private void shutDown() {
    deviceDaemon.refresh(this::shutDownDaemon);
  }

  @SuppressWarnings("SameReturnValue")
  private DeviceDaemon shutDownDaemon(Refreshable.Request<DeviceDaemon> request) {
    // Return null to indicate that a shutdown is requested.
    return null;
  }

  public enum State {INACTIVE, LOADING, READY}

  private static final Logger LOG = Logger.getInstance(DeviceService.class);
}

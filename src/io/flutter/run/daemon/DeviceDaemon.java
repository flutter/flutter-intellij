/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterMessages;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A process running 'flutter daemon' to watch for devices.
 */
class DeviceDaemon {
  /**
   * The command used to start this daemon.
   */
  private final @NotNull Command command;

  private final @NotNull ProcessHandler process;

  private final @NotNull AtomicReference<ImmutableList<FlutterDevice>> devices;

  private DeviceDaemon(@NotNull Command command, @NotNull ProcessHandler process,
                       @NotNull AtomicReference<ImmutableList<FlutterDevice>> devices) {
    this.command = command;
    this.process = process;
    this.devices = devices;
  }

  /**
   * Returns true if the process is still running.
   */
  boolean isRunning() {
    return !process.isProcessTerminating() && !process.isProcessTerminated();
  }

  /**
   * Returns the current devices.
   * <p>
   * <p>This is calculated based on add and remove events seen since the process started.
   */
  ImmutableList<FlutterDevice> getDevices() {
    return devices.get();
  }

  /**
   * Returns true if the daemon should be restarted.
   *
   * @param next the command that should be running now.
   */
  boolean needRestart(@NotNull Command next) {
    return !isRunning() || !command.equals(next);
  }

  /**
   * Kills the process.
   */
  void shutdown() {
    LOG.info("shutting down Flutter device poller: " + command.toString());
    process.destroyProcess();
  }

  /**
   * Returns the appropriate command to start the device daemon, if any.
   * <p>
   * A null means the device daemon should be shut down.
   */
  static @Nullable
  Command chooseCommand(Project project) {
    if (!usesFlutter(project)) {
      return null;
    }

    // See if the Bazel workspace provides a script.
    final Workspace w = WorkspaceCache.getInstance(project).getNow();
    if (w != null) {
      final String script = w.getDaemonScript();
      if (script != null) {
        return new Command(w.getRoot().getPath(), script, ImmutableList.of());
      }
    }

    // Otherwise, use the Flutter SDK.
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    try {
      final String path = FlutterSdkUtil.pathToFlutterTool(sdk.getHomePath());
      return new Command(sdk.getHomePath(), path, ImmutableList.of("daemon"));
    }
    catch (ExecutionException e) {
      LOG.warn("Unable to calculate command to watch Flutter devices", e);
      return null;
    }
  }

  private static boolean usesFlutter(Project p) {
    final Workspace w = WorkspaceCache.getInstance(p).getNow();
    if (w != null) {
      return w.usesFlutter(p);
    }
    else {
      return FlutterModuleUtils.hasFlutterModule(p);
    }
  }

  /**
   * The command used to start the daemon.
   * <p>
   * <p>Comparing two Commands lets us detect configuration changes that require a restart.
   */
  static class Command {
    /**
     * Path to working directory for running the script. Should be an absolute path.
     */
    private final @NotNull String workDir;
    private final @NotNull String command;
    private final @NotNull ImmutableList<String> parameters;

    private Command(@NotNull String workDir, @NotNull String command, @NotNull ImmutableList<String> parameters) {
      this.workDir = workDir;
      this.command = command;
      this.parameters = parameters;
    }

    /**
     * Launches the daemon.
     */
    DeviceDaemon start(Supplier<Boolean> isCancelled, Runnable deviceChanged) throws ExecutionException {
      LOG.info("starting Flutter device poller: " + toString());
      final ProcessHandler process = new OSProcessHandler(toCommandLine());
      boolean succeeded = false;
      try {
        final AtomicReference<ImmutableList<FlutterDevice>> devices = new AtomicReference<>(ImmutableList.of());

        final DaemonApi api = new DaemonApi(process);
        api.listen(process, new Listener(api, devices, deviceChanged));

        final Future ready = api.enableDeviceEvents();

        // Block until we get a response, or are cancelled.
        while (true) {
          if (isCancelled.get()) {
            throw new CancellationException();
          }

          try {
            ready.get(100, TimeUnit.MILLISECONDS);

            // Try not to show a partial device list.
            // It currently takes 4+ seconds for the devices to show up.
            // Remove when fixed: https://github.com/flutter/flutter/issues/8439
            Thread.sleep(4200);

            succeeded = true;
            return new DeviceDaemon(this, process, devices);
          }
          catch (TimeoutException e) {
            // Check for cancellation and try again.
          }
          catch (InterruptedException e) {
            throw new CancellationException();
          }
          catch (java.util.concurrent.ExecutionException e) {
            throw new ExecutionException(e.getCause());
          }
        }
      }
      finally {
        if (!succeeded) {
          process.destroyProcess();
        }
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Command)) {
        return false;
      }
      final Command other = (Command)obj;
      return Objects.equal(workDir, other.workDir)
             && Objects.equal(command, other.command)
             && Objects.equal(parameters, other.parameters);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(workDir, command, parameters);
    }

    private GeneralCommandLine toCommandLine() {
      final GeneralCommandLine result = new GeneralCommandLine().withWorkDirectory(workDir);
      result.setCharset(CharsetToolkit.UTF8_CHARSET);
      result.setExePath(FileUtil.toSystemDependentName(command));
      result.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());
      for (String param : parameters) {
        result.addParameter(param);
      }
      return result;
    }

    @Override
    public String toString() {
      final StringBuilder out = new StringBuilder();
      out.append(command);
      if (!parameters.isEmpty()) {
        out.append(' ');
        out.append(Joiner.on(' ').join(parameters));
      }
      return out.toString();
    }
  }

  /**
   * Handles events sent by the device daemon process.
   * <p>
   * <p>Updates the device list based on incoming events.
   */
  private static class Listener implements DaemonEvent.Listener {
    private final DaemonApi api;
    private final AtomicReference<ImmutableList<FlutterDevice>> devices;
    private final Runnable deviceChanged;

    Listener(DaemonApi api, AtomicReference<ImmutableList<FlutterDevice>> devices, Runnable deviceChanged) {
      this.api = api;
      this.devices = devices;
      this.deviceChanged = deviceChanged;
    }

    // daemon domain

    @Override
    public void onDaemonLogMessage(@NotNull DaemonEvent.LogMessage message) {
      LOG.info("flutter device watcher: " + message.message);
    }

    @Override
    public void onDaemonShowMessage(@NotNull DaemonEvent.ShowMessage event) {
      if ("error".equals(event.level)) {
        FlutterMessages.showError(event.title, event.message);
      }
      else if ("warning".equals(event.level)) {
        FlutterMessages.showWarning(event.title, event.message);
      }
      else {
        FlutterMessages.showInfo(event.title, event.message);
      }
    }

    // device domain

    public void onDeviceAdded(@NotNull DaemonEvent.DeviceAdded event) {
      if (event.id == null) {
        // We can't start a flutter app on this device if it doesn't have a device id.
        LOG.warn("Ignored an event from a Flutter process without a device id: " + event);
        return;
      }

      final FlutterDevice newDevice = new FlutterDevice(event.id,
                                                        event.name == null ? event.id : event.name,
                                                        event.platform,
                                                        event.emulator);
      devices.updateAndGet((old) -> addDevice(old.stream(), newDevice));
      deviceChanged.run();
    }

    public void onDeviceRemoved(@NotNull DaemonEvent.DeviceRemoved event) {
      devices.updateAndGet((old) -> removeDevice(old.stream(), event.id));
      deviceChanged.run();
    }

    // helpers

    private static ImmutableList<FlutterDevice> addDevice(Stream<FlutterDevice> old, FlutterDevice newDevice) {
      final List<FlutterDevice> changed = new ArrayList<>();
      changed.addAll(removeDevice(old, newDevice.deviceId()));
      changed.add(newDevice);
      changed.sort(Comparator.comparing(FlutterDevice::deviceName));
      return ImmutableList.copyOf(changed);
    }

    private static ImmutableList<FlutterDevice> removeDevice(Stream<FlutterDevice> old, String idToRemove) {
      return ImmutableList.copyOf(old.filter((d) -> !d.deviceId().equals(idToRemove)).iterator());
    }
  }

  private static final Logger LOG = Logger.getInstance(DeviceDaemon.class);
}

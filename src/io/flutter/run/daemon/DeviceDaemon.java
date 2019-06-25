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
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.android.IntelliJAndroidSdk;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.MostlySilentOsProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A process running 'flutter daemon' to watch for devices.
 */
class DeviceDaemon {
  private static final AtomicInteger nextDaemonId = new AtomicInteger();

  /**
   * Attempt to start the daemon this many times before showing the user a warning that the daemon is having trouble starting up.
   */
  private static final int RESTART_ATTEMPTS_BEFORE_WARNING = 100;

  /**
   * A unique id used to log device daemon actions.
   */
  private final int id;

  /**
   * The command used to start this daemon.
   */
  @NotNull private final Command command;

  @NotNull private final ProcessHandler process;

  @NotNull private final Listener listener;

  @NotNull private final AtomicReference<ImmutableList<FlutterDevice>> devices;

  private DeviceDaemon(int id,
                       @NotNull Command command, @NotNull ProcessHandler process, @NotNull Listener listener,
                       @NotNull AtomicReference<ImmutableList<FlutterDevice>> devices) {
    this.id = id;
    this.command = command;
    this.process = process;
    this.listener = listener;
    this.devices = devices;
    listener.running.set(true);
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
   * Kills the process. (Normal shutdown.)
   */
  void shutdown() {
    if (!process.isProcessTerminated()) {
      LOG.info("shutting down Flutter device daemon #" + id + ": " + command.toString());
    }
    listener.running.set(false);
    process.destroyProcess();
  }

  /**
   * Returns the appropriate command to start the device daemon, if any.
   * <p>
   * A null means the device daemon should be shut down.
   */
  @Nullable
  static Command chooseCommand(@NotNull final Project project) {
    if (!usesFlutter(project)) {
      return null;
    }

    final String androidHome = IntelliJAndroidSdk.chooseAndroidHome(project, false);

    // See if the Bazel workspace provides a script.
    final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
    if (workspace != null) {
      final String script = workspace.getDaemonScript();
      if (script != null) {
        return new Command(workspace.getRoot().getPath(), script, ImmutableList.of(), androidHome);
      }
    }

    // Otherwise, use the Flutter SDK.
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    try {
      final String path = FlutterSdkUtil.pathToFlutterTool(sdk.getHomePath());
      final ImmutableList<String> list;
      if (FlutterUtils.isIntegrationTestingMode()) {
        list = ImmutableList.of("--show-test-device", "daemon");
      }
      else {
        list = ImmutableList.of("daemon");
      }
      return new Command(sdk.getHomePath(), path, list, androidHome);
    }
    catch (ExecutionException e) {
      FlutterUtils.warn(LOG, "Unable to calculate command to watch Flutter devices", e);
      return null;
    }
  }

  private static boolean usesFlutter(@NotNull final Project project) {
    return FlutterModuleUtils.isFlutterBazelProject(project) || FlutterModuleUtils.hasFlutterModule(project);
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
    @NotNull private final String workDir;
    @NotNull private final String command;
    @NotNull private final ImmutableList<String> parameters;

    /**
     * The value of ANDROID_HOME to use when launching the command.
     */
    @Nullable private final String androidHome;

    private Command(@NotNull String workDir, @NotNull String command, @NotNull ImmutableList<String> parameters,
                    @Nullable String androidHome) {
      this.workDir = workDir;
      this.command = command;
      this.parameters = parameters;
      this.androidHome = androidHome;
    }

    /**
     * Launches the daemon.
     *
     * @param isCancelled    will be polled during startup to see if startup is cancelled.
     * @param deviceChanged  will be called whenever a device is added or removed from the returned DeviceDaemon.
     * @param processStopped will be called if the process exits unexpectedly after this method returns.
     */
    DeviceDaemon start(Supplier<Boolean> isCancelled,
                       Runnable deviceChanged,
                       Consumer<String> processStopped) throws ExecutionException {
      final int daemonId = nextDaemonId.incrementAndGet();
      LOG.info("starting Flutter device daemon #" + daemonId + ": " + toString());
      // The mostly silent process handler reduces CPU usage of the daemon process.
      final ProcessHandler process = new MostlySilentOsProcessHandler(toCommandLine());

      boolean succeeded = false;
      try {
        final AtomicReference<ImmutableList<FlutterDevice>> devices = new AtomicReference<>(ImmutableList.of());

        final DaemonApi api = new DaemonApi(process);
        final Listener listener = new Listener(daemonId, api, devices, deviceChanged, processStopped);
        api.listen(process, listener);

        final Future ready = api.enableDeviceEvents();

        // Block until we get a response, or are cancelled.
        int attempts = 0;
        while (true) {
          if (isCancelled.get()) {
            throw new CancellationException();
          }
          else if (process.isProcessTerminated()) {
            final Integer exitCode = process.getExitCode();
            final String failureMessage = ("Flutter device daemon #" +
                                           daemonId +
                                           ": process exited during startup. Exit code: " +
                                           exitCode +
                                           ", stderr:\n" +
                                           api.getStderrTail());
            attempts++;
            if (attempts < DeviceDaemon.RESTART_ATTEMPTS_BEFORE_WARNING) {
              LOG.warn(failureMessage);
            }
            else {
              // IntelliJ will show a generic failure message the first time we log this error.
              LOG.error(failureMessage);
              // The second time we log this error, we'll show a customized message to alert the user to the specific problem.
              if (attempts == DeviceDaemon.RESTART_ATTEMPTS_BEFORE_WARNING + 1) {
                // Show a message in the UI when we reach the warning threshold.
                FlutterMessages.showError("Flutter device daemon", failureMessage);
              }
            }
          }

          try {
            // Retry with a longer delay if we are encountering repeated failures of the daemon.
            ready.get(attempts < DeviceDaemon.RESTART_ATTEMPTS_BEFORE_WARNING ? 100 : 10000, TimeUnit.MILLISECONDS);

            succeeded = true;
            return new DeviceDaemon(daemonId, this, process, listener, devices);
          }
          catch (TimeoutException e) {
            // Check for cancellation and try again.
          }
          catch (InterruptedException e) {
            throw new CancellationException();
          }
          catch (java.util.concurrent.ExecutionException e) {
            // This is not a user facing crash - we log (and no devices will be discovered).
            FlutterUtils.warn(LOG, e.getCause());
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
             && Objects.equal(parameters, other.parameters)
             && Objects.equal(androidHome, other.androidHome);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(workDir, command, parameters, androidHome);
    }

    private GeneralCommandLine toCommandLine() {
      final GeneralCommandLine result = new GeneralCommandLine().withWorkDirectory(workDir);
      result.setCharset(CharsetToolkit.UTF8_CHARSET);
      result.setExePath(FileUtil.toSystemDependentName(command));
      result.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());
      if (FlutterSettings.getInstance().isShowWebDesktopDevices()) {
        result.withEnvironment("ENABLE_FLUTTER_DESKTOP", "true");
        result.withEnvironment("FLUTTER_WEB", "true");
      }
      if (androidHome != null) {
        result.withEnvironment("ANDROID_HOME", androidHome);
      }
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
    private final int daemonId;
    private final DaemonApi api;
    private final AtomicReference<ImmutableList<FlutterDevice>> devices;
    private final Runnable deviceChanged;
    private final Consumer<String> processStopped;

    private final AtomicBoolean running = new AtomicBoolean(false);

    Listener(int daemonId,
             DaemonApi api,
             AtomicReference<ImmutableList<FlutterDevice>> devices,
             Runnable deviceChanged,
             Consumer<String> processStopped) {
      this.daemonId = daemonId;
      this.api = api;
      this.devices = devices;
      this.deviceChanged = deviceChanged;
      this.processStopped = processStopped;
    }

    // daemon domain

    @Override
    public void onDaemonLogMessage(@NotNull DaemonEvent.DaemonLogMessage message) {
      LOG.info("flutter device daemon #" + daemonId + ": " + message.message);
    }

    @Override
    public void onDaemonShowMessage(@NotNull DaemonEvent.DaemonShowMessage event) {
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
        FlutterUtils.warn(LOG, "Ignored an event from a Flutter process without a device id: " + event);
        return;
      }

      final FlutterDevice newDevice = new FlutterDevice(event.id,
                                                        event.name == null ? event.id : event.name,
                                                        event.platform,
                                                        event.emulator,
                                                        event.category,
                                                        event.platformType,
                                                        event.ephemeral);
      devices.updateAndGet((old) -> addDevice(old.stream(), newDevice));
      deviceChanged.run();
    }

    public void onDeviceRemoved(@NotNull DaemonEvent.DeviceRemoved event) {
      devices.updateAndGet((old) -> removeDevice(old.stream(), event.id));
      deviceChanged.run();
    }

    @Override
    public void processTerminated(int exitCode) {
      if (running.get()) {
        processStopped.accept(
          "Daemon #" + daemonId + " exited. Exit code: " + exitCode + ". Stderr:\n" +
          api.getStderrTail());
      }
    }

    // helpers

    private static ImmutableList<FlutterDevice> addDevice(Stream<FlutterDevice> old, FlutterDevice newDevice) {
      final List<FlutterDevice> changed = new ArrayList<>(removeDevice(old, newDevice.deviceId()));
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

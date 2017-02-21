/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
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
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A process running 'flutter daemon' to watch for devices.
 */
class DeviceDaemon {
  /**
   * The command used to start this daemon.
   */
  private final @NotNull Command command;

  private final @NotNull FlutterDaemonController controller;

  private DeviceDaemon(@NotNull Command command, @NotNull FlutterDaemonController controller) {
    this.command = command;
    this.controller = controller;
  }

  /**
   * Returns true if the process is still running.
   */
  public boolean isRunning() {
    final ProcessHandler handler = controller.getProcessHandler();
    return handler != null && !handler.isProcessTerminating();
  }

  /**
   * Returns true if the daemon should be restarted.
   *
   * @param next the command that should be running now.
   */
  public boolean needRestart(@NotNull Command next) {
    return !isRunning() || !command.equals(next);
  }

  /**
   * Kills the process.
   */
  public void shutdown() {
    controller.forceExit();
  }

  /**
   * Returns the appropriate command to start the device daemon, if any.
   *
   * A null means the device daemon should be shut down.
   */
  static @Nullable Command chooseCommand(Project project) {
    // If an SDK is configured, prefer using it to the Bazel script.
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk != null) {
      // Only enable this for projects containing flutter modules.
      if (!FlutterModuleUtils.hasFlutterModule(project)) return null;

      try {
        final String path = FlutterSdkUtil.pathToFlutterTool(sdk.getHomePath());
        return new Command(sdk.getHomePath(), path, ImmutableList.of("daemon"));
      }
      catch (ExecutionException e) {
        LOG.warn("Unable to calculate command to watch Flutter devices", e);
        return null;
      }
    }

    // See if the Bazel project provides a script.
    final Workspace w = WorkspaceCache.getInstance(project).getNow();
    if (w == null) return null;

    final String script = w.getDaemonScript();
    if (script == null) return null;

    return new Command(w.getRoot().getPath(), script, ImmutableList.of());
  }

  /**
   * The command used to start the daemon.
   *
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
    DeviceDaemon start(FlutterDaemonService service) throws ExecutionException {
      final FlutterDaemonController controller = new FlutterDaemonController(service);
      controller.startDevicePoller(toCommandLine());
      return new DeviceDaemon(this, controller);
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

  private static final Logger LOG = Logger.getInstance(DeviceDaemon.class);
}

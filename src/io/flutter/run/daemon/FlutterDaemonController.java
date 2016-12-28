/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Control an external Flutter process, including reading events and responses from its stdout and
 * writing commands to its stdin.
 */
public class FlutterDaemonController extends ProcessAdapter {
  private static final Logger LOG = Logger.getInstance(FlutterDaemonController.class.getName());

  private static final String STDOUT_KEY = "stdout";

  @NotNull
  private final FlutterDaemonService myService;
  private final FlutterDaemonControllerHelper myControllerHelper;
  private final List<DaemonListener> myListeners = Collections.synchronizedList(new ArrayList<>());
  private ProcessHandler myProcessHandler;
  private boolean myIsPollingController = false;
  private boolean myIsPollingStarted = false;

  public FlutterDaemonController(@NotNull FlutterDaemonService service) {
    myService = service;
    myControllerHelper = new FlutterDaemonControllerHelper(this);
  }

  @NotNull
  protected FlutterDaemonService getService() {
    return myService;
  }

  void addDaemonListener(DaemonListener listener) {
    if (!myListeners.contains(listener)) {
      myListeners.add(listener);
    }
  }

  void removeDaemonListener(DaemonListener listener) {
    myListeners.remove(listener);
  }

  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public void forceExit() {
    if (myProcessHandler != null) {
      myProcessHandler.destroyProcess();
    }
    myProcessHandler = null;
  }

  void startDevicePoller() throws ExecutionException {
    myIsPollingController = true;

    final GeneralCommandLine commandLine = createCommandLinePoller();
    myProcessHandler = new OSProcessHandler(commandLine);
    myProcessHandler.addProcessListener(this);
    myProcessHandler.startNotify();
  }

  public FlutterApp startRunnerProcess(@NotNull Project project,
                                       @NotNull String projectDir,
                                       @Nullable String deviceId,
                                       @NotNull RunMode mode,
                                       boolean startPaused,
                                       boolean isHot,
                                       @Nullable String target) throws ExecutionException {
    final GeneralCommandLine commandLine = createCommandLineRunner(project, projectDir, deviceId, mode, startPaused, isHot, target);
    myProcessHandler = new OSProcessHandler(commandLine);
    myProcessHandler.addProcessListener(this);
    myProcessHandler.startNotify();
    return myControllerHelper.appStarting(deviceId, mode, project, startPaused, isHot);
  }

  public FlutterApp startBazelProcess(@NotNull Project project,
                                      @NotNull String projectDir,
                                      @Nullable String deviceId,
                                      @NotNull RunMode mode,
                                      boolean startPaused,
                                      boolean isHot,
                                      @NotNull String launchingScript,
                                      @NotNull String bazelTarget,
                                      @Nullable String additionalArguments) throws ExecutionException {
    final GeneralCommandLine commandLine =
      createBazelRunner(project, projectDir, deviceId, mode, startPaused, isHot, launchingScript, bazelTarget, additionalArguments);
    myProcessHandler = new OSProcessHandler(commandLine);
    myProcessHandler.addProcessListener(this);
    myProcessHandler.startNotify();
    return myControllerHelper.appStarting(deviceId, mode, project, startPaused, isHot);
  }

  public void sendCommand(String commandJson, FlutterDaemonControllerHelper manager) {
    if (myProcessHandler == null) {
      return; // Possibly, device was removed TODO(messick) Handle disconnecting the device
    }
    final OutputStream input = myProcessHandler.getProcessInput();
    if (input == null) {
      LOG.error("No process input");
      return;
    }
    try (FlutterStream str = new FlutterStream(input)) {
      str.print("[");
      str.print(commandJson);
      str.println("]");
    }
  }

  @Override
  public void processTerminated(ProcessEvent event) {
    for (DaemonListener listener : myListeners.toArray(new DaemonListener[0])) {
      listener.processTerminated(event.getProcessHandler(), this);
    }
  }

  @Override
  public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
    for (DaemonListener listener : myListeners.toArray(new DaemonListener[0])) {
      listener.aboutToTerminate(event.getProcessHandler(), this);
    }
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    if (outputType.toString().equals(STDOUT_KEY)) {
      String text = event.getText().trim();
      if (text.startsWith("[{") && text.endsWith("}]")) {
        text = text.substring(1, text.length() - 1);
        for (DaemonListener listener : myListeners) {
          listener.daemonInput(text, this);
        }
      }

      if (myIsPollingController && !myIsPollingStarted) {
        myIsPollingStarted = true;

        for (DaemonListener listener : myListeners) {
          myControllerHelper.enableDevicePolling();
        }
      }
    }
  }

  /**
   * Create a command to start the Flutter daemon when used for purposes of polling.
   */
  private static GeneralCommandLine createCommandLinePoller() throws ExecutionException {
    String flutterSdkPath = null;
    final FlutterSdk flutterSdk = FlutterSdk.getGlobalFlutterSdk();
    if (flutterSdk != null) {
      flutterSdkPath = flutterSdk.getHomePath();
    }

    if (flutterSdkPath == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }

    final String flutterExec = FlutterSdkUtil.pathToFlutterTool(flutterSdkPath);

    // While not strictly required, we set the working directory to the flutter root for consistency.
    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(flutterSdkPath);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(flutterExec));
    commandLine.addParameter("daemon");

    return commandLine;
  }

  /**
   * Create a command to run 'flutter run --machine'.
   */
  private static GeneralCommandLine createCommandLineRunner(@NotNull Project project,
                                                            @NotNull String projectDir,
                                                            @Nullable String deviceId,
                                                            @NotNull RunMode mode,
                                                            boolean startPaused,
                                                            boolean isHot,
                                                            @Nullable String target) throws ExecutionException {
    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }
    final String flutterSdkPath = flutterSdk.getHomePath();
    final String flutterExec = FlutterSdkUtil.pathToFlutterTool(flutterSdkPath);

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(projectDir);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(flutterExec));
    commandLine.addParameters("run", "--machine");
    if (deviceId != null) {
      commandLine.addParameter("--device-id=" + deviceId);
    }
    if (mode == RunMode.PROFILE) {
      commandLine.addParameter("--profile");
    }
    if (startPaused) {
      commandLine.addParameter("--start-paused");
    }
    if (!isHot) {
      commandLine.addParameter("--no-hot");
    }
    if (target != null) {
      commandLine.addParameter(target);
    }
    return commandLine;
  }

  private static GeneralCommandLine createBazelRunner(@NotNull Project project,
                                                      @NotNull String projectDir,
                                                      @Nullable String deviceId,
                                                      @NotNull RunMode mode,
                                                      boolean startPaused,
                                                      boolean isHot,
                                                      @NotNull String launchingScript,
                                                      @NotNull String bazelTarget,
                                                      @Nullable String additionalArguments) {
    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(projectDir);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    // TODO(devoncarew): Fix the launch script to accept more flags.
    //commandLine.addParameters("--machine");

    // TODO(devoncarew): Fix the launch script to accept more flags.
    //if (deviceId != null) {
    //  commandLine.addParameter("--device-id=" + deviceId);
    //}

    // TODO(devoncarew): Fix the launch script to accept more flags.
    //if (startPaused) {
    //  commandLine.addParameter("--start-paused");
    //}

    // Set the mode.
    if (mode != RunMode.DEBUG) {
      commandLine.addParameters("--define", "flutter_build_mode=" + mode.name());
    }

    // Additional arguments.
    if (additionalArguments != null) {
      final StringTokenizer argumentsTokenizer = new CommandLineTokenizer(additionalArguments);
      while (argumentsTokenizer.hasMoreTokens()) {
        commandLine.addParameter(argumentsTokenizer.nextToken());
      }
    }

    // Append _run[_hot] to bazelTarget.
    if (!bazelTarget.endsWith("_run") && !bazelTarget.endsWith(("_hot"))) {
      bazelTarget += "_run";
      if (isHot) {
        bazelTarget += "_hot";
      }
    }
    commandLine.addParameter(bazelTarget);

    return commandLine;
  }

  private static class FlutterStream extends PrintStream {
    public FlutterStream(@NotNull OutputStream out) {
      super(out);
    }

    @Override
    public void close() {
      // Closing the stream terminates the process, so don't do it.
      flush();
    }
  }
}

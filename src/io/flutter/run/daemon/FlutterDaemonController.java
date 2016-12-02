/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ExecutionException;
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

/**
 * Control an external Flutter process, including reading events and responses from its stdout and
 * writing commands to its stdin.
 */
public class FlutterDaemonController extends ProcessAdapter {
  private static final Logger LOG = Logger.getInstance(FlutterDaemonController.class.getName());
  private static final String STDOUT_KEY = "stdout";

  private final String myProjectDirectory;
  private final List<String> myDeviceIds = new ArrayList<>();
  private final List<DaemonListener> myListeners = Collections.synchronizedList(new ArrayList<>());
  private ProcessHandler myHandler;
  private boolean myIsPollingController = false;
  private boolean myIsPollingStarted = false;

  public FlutterDaemonController() {
    myProjectDirectory = null;
  }

  public FlutterDaemonController(String projectDir) {
    myProjectDirectory = projectDir;
  }

  public void addListener(DaemonListener listener) {
    removeListener(listener);
    myListeners.add(listener);
  }

  public ProcessHandler getProcessHandler() {
    return myHandler;
  }

  public void removeListener(DaemonListener listener) {
    myListeners.remove(listener);
  }

  public void forceExit() {
    // TODO Stop all apps and terminate the external process
    if (myHandler != null) {
      myHandler.destroyProcess();
    }
    myDeviceIds.clear();
    myHandler = null;
  }

  public String getProjectDirectory() {
    return myProjectDirectory;
  }

  /**
   * Return true if this controller can be used by the app in the projectDir.
   * The very first controller that is created (to do device discovery) has
   * no project directory so it can be reused. Always invoke #setProjectAndDevice
   * after selecting a controller to ensure both directory and device id are set.
   *
   * @param projectDir The path to a project directory
   * @return true if the instance can be used or reused
   */
  public boolean isForProject(String projectDir) {
    return myProjectDirectory == null || myProjectDirectory.equals(projectDir);
  }

  boolean hasDeviceId(String id) {
    return myDeviceIds.contains(id);
  }

  private void addDeviceId(String deviceId) {
    myDeviceIds.add(deviceId);
  }

  void removeDeviceId(String deviceId) {
    myDeviceIds.remove(deviceId);
  }

  void startDevicePoller() throws ExecutionException {
    myIsPollingController = true;

    final GeneralCommandLine commandLine = createCommandLinePoller();
    myHandler = new OSProcessHandler(commandLine);
    myHandler.addProcessListener(this);
    myHandler.startNotify();
  }

  public void startRunnerDaemon(
    @NotNull Project project,
    @NotNull String deviceId,
    @NotNull RunMode mode,
    boolean startPaused,
    boolean isHot,
    @Nullable String target) throws ExecutionException {

    // TODO(devoncarew): Use the deviceId / mode / startPaused / isHot / target params.
    final GeneralCommandLine commandLine = createCommandLineRunner(project);
    myHandler = new OSProcessHandler(commandLine);
    myHandler.addProcessListener(this);
    myHandler.startNotify();
  }

  public void sendCommand(String commandJson, FlutterAppManager manager) {
    if (myHandler == null) {
      return; // Possibly, device was removed TODO(messick) Handle disconnecting the device
    }
    final OutputStream input = myHandler.getProcessInput();
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
          listener.enableDevicePolling(this);
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
   * Create a command to start the Flutter daemon when used to launch apps.
   */
  private static GeneralCommandLine createCommandLineRunner(@NotNull Project project) throws ExecutionException {
    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }
    final String flutterSdkPath = flutterSdk.getHomePath();
    final String flutterExec = FlutterSdkUtil.pathToFlutterTool(flutterSdkPath);

    // While not strictly required, we set the working directory to the flutter root for consistency.
    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(flutterSdkPath);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(flutterExec));
    commandLine.addParameter("daemon");

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

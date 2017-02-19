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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

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

  void addProcessTerminatedListener(Consumer<FlutterDaemonController> callback) {
    addDaemonListener(new DaemonListener() {
      @Override
      public void daemonInput(String json, FlutterDaemonController controller) {

      }

      @Override
      public void aboutToTerminate(ProcessHandler handler, FlutterDaemonController controller) {

      }

      @Override
      public void processTerminated(ProcessHandler handler, FlutterDaemonController controller) {
        callback.accept(controller);
      }
    });
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

  void startDevicePoller(GeneralCommandLine command) throws ExecutionException {
    myIsPollingController = true;
    startProcess(command);
  }

  public FlutterApp startRunnerProcess(@NotNull Project project,
                                       @NotNull String projectDir,
                                       @Nullable String deviceId,
                                       @NotNull RunMode mode,
                                       boolean startPaused,
                                       boolean isHot,
                                       @Nullable String target) throws ExecutionException {
    final GeneralCommandLine commandLine = createCommandLineRunner(project, projectDir, deviceId, mode, startPaused, isHot, target);
    startProcess(commandLine);

    // Send analytics for the start and stop events.
    FlutterInitializer.sendAnalyticsAction(StringUtil.capitalize(mode.mode()) + "App");
    myProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        FlutterInitializer.sendAnalyticsAction("StopApp");
      }
    });

    return myControllerHelper.appStarting(deviceId, mode, project, startPaused, isHot);
  }

  public FlutterApp startBazelProcess(@NotNull Project project,
                                      @NotNull String projectDir,
                                      @Nullable FlutterDevice device,
                                      @NotNull RunMode mode,
                                      boolean startPaused,
                                      boolean isHot,
                                      @NotNull String launchingScript,
                                      @NotNull String bazelTarget,
                                      @Nullable String additionalArguments) throws ExecutionException {
    final GeneralCommandLine commandLine =
      createBazelRunner(project, projectDir, device, mode, startPaused, isHot, launchingScript, bazelTarget, additionalArguments);
    startProcess(commandLine);

    // Send analytics for the start and stop events.
    FlutterInitializer.sendAnalyticsAction(StringUtil.capitalize(mode.mode()) + "BazelApp");
    myProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        FlutterInitializer.sendAnalyticsAction("StopBazelApp");
      }
    });

    return myControllerHelper.appStarting(device == null ? null : device.deviceId(), mode, project, startPaused, isHot);
  }

  private void startProcess(GeneralCommandLine commandLine) throws ExecutionException {
    myProcessHandler = new OSProcessHandler(commandLine);
    myProcessHandler.addProcessListener(this);
    myProcessHandler.startNotify();
  }

  public void sendCommand(String commandJson, FlutterDaemonControllerHelper manager) {
    if (myProcessHandler == null) {
      return;
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
                                                      @Nullable FlutterDevice device,
                                                      @NotNull RunMode mode,
                                                      boolean startPaused,
                                                      boolean isHot,
                                                      @NotNull String launchingScript,
                                                      @NotNull String bazelTarget,
                                                      @Nullable String additionalArguments) {
    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(projectDir);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    // Set the mode.
    if (mode != RunMode.DEBUG) {
      commandLine.addParameters("--define", "flutter_build_mode=" + mode.name());
    }

    // Send in platform architecture based in the device info.
    if (device != null) {
      final String platform = device.platform();

      if (device.isIOS()) {
        // --ios_cpu=[arm64, x86_64]
        final String arch = device.emulator() ? "x86_64" : "arm64";
        commandLine.addParameter("--ios_cpu=" + arch);
      }
      else {
        // --android_cpu=[armeabi, x86, x86_64]
        String arch = null;

        switch (platform) {
          case "android-arm":
            arch = "armeabi";
            break;
          case "android-x86":
            arch = "x86";
            break;
          case "android-x64":
            arch = "x86_64";
            break;
          case "linux-x64":
            arch = "x86_64";
            break;
        }

        if (arch != null) {
          commandLine.addParameter("--android_cpu=" + arch);
        }
      }
    }

    // User specified additional arguments.
    final CommandLineTokenizer argumentsTokenizer = new CommandLineTokenizer(StringUtil.notNullize(additionalArguments));
    while (argumentsTokenizer.hasMoreTokens()) {
      final String token = argumentsTokenizer.nextToken();
      if (token.equals("--")) {
        break;
      }
      commandLine.addParameter(token);
    }

    // Append _run[_hot] to bazelTarget.
    if (!bazelTarget.endsWith("_run") && !bazelTarget.endsWith(("_hot"))) {
      bazelTarget += "_run";
      if (isHot) {
        bazelTarget += "_hot";
      }
    }
    commandLine.addParameter(bazelTarget);

    // Pass additional args to bazel (we currently don't pass --device-id with bazel targets).
    commandLine.addParameter("--");

    // Tell the flutter tommand-line tools that we want a machine interface on stdio.
    commandLine.addParameters("--machine");

    // Pause the app at startup in order to set breakpoints.
    if (startPaused) {
      commandLine.addParameter("--start-paused");
    }

    // More user-specified args.
    while (argumentsTokenizer.hasMoreTokens()) {
      commandLine.addParameter(argumentsTokenizer.nextToken());
    }

    // Send in the deviceId.
    if (device != null) {
      commandLine.addParameter("-d");
      commandLine.addParameter(device.deviceId());
    }

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

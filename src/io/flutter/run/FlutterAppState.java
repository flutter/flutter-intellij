/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.NetUtils;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunningState;
import com.jetbrains.lang.dart.ide.runner.server.OpenDartObservatoryUrlAction;
import io.flutter.run.daemon.ConnectedDevice;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDaemonService;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FlutterAppState extends DartCommandLineRunningState {

  private static final String RUN = DefaultRunExecutor.EXECUTOR_ID;
  private FlutterApp myApp;
  private RunMode myMode;

  protected FlutterAppState(ExecutionEnvironment environment) throws ExecutionException {
    super(environment);
    String mode = environment.getExecutor().getId();
    if (DefaultRunExecutor.EXECUTOR_ID.equals(mode)) {
      myMode = RunMode.RELEASE;
    }
    else if (DefaultDebugExecutor.EXECUTOR_ID.equals(mode)) {
      myMode = RunMode.DEBUG;
    }
    else {
      myMode = RunMode.PROFILE;
    }
  }

  /**
   * Starts the process.
   *
   * @return the handler for the running process
   * @throws ExecutionException if the execution failed.
   * @see GeneralCommandLine
   * @see com.intellij.execution.process.OSProcessHandler
   */
  @NotNull
  protected ProcessHandler startProcess() throws ExecutionException {
    FlutterDaemonService service = FlutterDaemonService.getInstance();
    assert service != null;
    Project project = getEnvironment().getProject();
    String workingDir = project.getBasePath();
    assert workingDir != null;

    Collection<ConnectedDevice> devices = service.getConnectedDevices();
    if (devices.isEmpty()) {
      throw new ExecutionException("No connected device");
    }

    ConnectedDevice device = service.getSelectedDevice();
    if (device == null) {
      throw new ExecutionException("No selected device");
    }

    FlutterRunnerParameters parameters = ((FlutterRunConfiguration)getEnvironment().getRunProfile()).getRunnerParameters().clone();
    final String cwd = parameters.computeProcessWorkingDirectory(project);

    String relativePath = parameters.getFilePath();
    if (relativePath != null && relativePath.startsWith(cwd)) {
      relativePath = relativePath.substring(cwd.length());
      if (relativePath.startsWith(File.separator)) {
        relativePath = relativePath.substring(1);
      }
    }

    myApp = service.startApp(project, cwd, device.deviceId(), myMode, relativePath);
    return myApp.getController().getProcessHandler();
  }

  protected ConsoleView createConsole(@NotNull final Executor executor) throws ExecutionException {
    ConsoleView console = super.createConsole(executor);
    myApp.setConsole(console);
    return console;
  }

  protected void addObservatoryActions(List<AnAction> actions, final ProcessHandler processHandler) {
    actions.add(new Separator());
    actions.add(new OpenDartObservatoryUrlAction(
      "http://" + NetUtils.getLocalHostString() + ":" + myApp.port(),
      () -> !processHandler.isProcessTerminated()));
  }

  public boolean isConnectionReady() {
    return myApp != null && myApp.port() > 0;
  }

  public int getObservatoryPort() {
    return myApp.port();
  }

  public FlutterApp getApp() {
    return myApp;
  }
}

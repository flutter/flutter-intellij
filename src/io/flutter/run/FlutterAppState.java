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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.NetUtils;
import com.jetbrains.lang.dart.ide.runner.server.OpenDartObservatoryUrlAction;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.run.daemon.ConnectedDevice;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDaemonService;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class FlutterAppState extends FlutterAppStateBase {
  private static final String RUN = DefaultRunExecutor.EXECUTOR_ID;
  private FlutterApp myApp;
  private final RunMode myMode;

  protected FlutterAppState(ExecutionEnvironment environment) throws ExecutionException {
    super(environment);
    final String mode = environment.getExecutor().getId();
    if (DefaultRunExecutor.EXECUTOR_ID.equals(mode)) {
      myMode = RunMode.RUN;
    }
    else if (DefaultDebugExecutor.EXECUTOR_ID.equals(mode)) {
      myMode = RunMode.DEBUG;
    }
    else {
      myMode = RunMode.PROFILE;
    }
  }

  @NotNull
  public RunMode getMode() {
    return myMode;
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
    final FlutterDaemonService service = FlutterDaemonService.getInstance(getEnvironment().getProject());
    assert service != null;

    final Project project = getEnvironment().getProject();
    final String workingDir = project.getBasePath();
    assert workingDir != null;

    final Collection<ConnectedDevice> devices = service.getConnectedDevices();
    if (devices.isEmpty()) {
      throw new ExecutionException("No connected device");
    }

    final ConnectedDevice device = service.getSelectedDevice();
    if (device == null) {
      throw new ExecutionException("No selected device");
    }

    final FlutterRunnerParameters parameters = ((FlutterRunConfiguration)getEnvironment().getRunProfile()).getRunnerParameters().clone();
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
    final ConsoleView console = super.createConsole(executor);
    myApp.setConsole(console);
    if (console != null) {
      final Project project = getEnvironment().getProject();
      final FlutterRunnerParameters parameters = ((FlutterRunConfiguration)getEnvironment().getRunProfile()).getRunnerParameters().clone();
      final String path = parameters.getFilePath();
      if (path != null) {
        final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file != null) {
          final Module module = ModuleUtil.findModuleForFile(file, project);
          if (module != null) {
            console.addMessageFilter(new FlutterConsoleFilter(module));
          }
        }
      }
    }
    return console;
  }

  protected void addObservatoryActions(List<AnAction> actions, final ProcessHandler processHandler) {
    actions.add(new Separator());
    actions.add(new OpenDartObservatoryUrlAction(
      "http://" + NetUtils.getLocalHostString() + ":" + myApp.port(), //NON-NLS
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

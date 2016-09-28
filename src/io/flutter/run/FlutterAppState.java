/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.NetUtils;
import com.jetbrains.lang.dart.ide.runner.DartConsoleFilter;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunningState;
import com.jetbrains.lang.dart.ide.runner.server.OpenDartObservatoryUrlAction;
import io.flutter.run.daemon.ConnectedDevice;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDaemonService;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FlutterAppState extends DartCommandLineRunningState {

  private FlutterApp myApp;
  private ConsoleView myConsole;

  protected FlutterAppState(ExecutionEnvironment environment) throws ExecutionException {
    super(environment);
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
    if (device == null){
      throw new ExecutionException("No selected device");
    }

    myApp = service.startApp(project, workingDir, device.deviceId(), RunMode.DEBUG); // TODO Select run mode based on launch.
    return myApp.getController().getProcessHandler();
  }

  @Override
  protected AnAction[] createActions(final ConsoleView console, final ProcessHandler processHandler, final Executor executor) {
    // These actions are effectively added only to the Run tool window. For Debug see DartCommandLineDebugProcess.registerAdditionalActions()
    final List<AnAction> actions = new ArrayList<>(Arrays.asList(super.createActions(console, processHandler, executor)));
    addObservatoryActions(actions, processHandler);
    return actions.toArray(new AnAction[actions.size()]);
  }

  protected ConsoleView createConsole(@NotNull final Executor executor) throws ExecutionException {
    myConsole = super.createConsole(executor);
    myApp.setConsole(myConsole);
    return myConsole;
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

  private static class JsonStringFilter implements Filter {

    @Nullable
    @Override
    public Result applyFilter(String line, int entireLength) {
      if (line.startsWith("[{") && line.endsWith("}]\n"))
        return new Result(0, entireLength, null);
      return null;
    }
  }
}

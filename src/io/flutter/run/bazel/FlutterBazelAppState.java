/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import io.flutter.run.FlutterAppState;
import io.flutter.run.FlutterRunConfigurationBase;
import io.flutter.run.FlutterRunnerParameters;
import io.flutter.run.daemon.DeviceService;
import io.flutter.run.daemon.FlutterDaemonService;
import io.flutter.run.daemon.FlutterDevice;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class FlutterBazelAppState extends FlutterAppState {
  public FlutterBazelAppState(ExecutionEnvironment environment) throws ExecutionException {
    super(environment);
  }

  @Override
  protected void checkConfiguration() throws RuntimeConfigurationError {
    myRunnerParameters.checkForBazelLaunch(getEnvironment().getProject());
  }

  @NotNull
  protected ProcessHandler startProcess() throws ExecutionException {
    final Project project = getEnvironment().getProject();

    final String workingDir = project.getBasePath();
    assert workingDir != null;

    final FlutterDevice device = DeviceService.getInstance(project).getSelectedDevice();

    final FlutterRunnerParameters parameters =
      ((FlutterRunConfigurationBase)getEnvironment().getRunProfile()).getRunnerParameters().clone();
    final String cwd = parameters.computeProcessWorkingDirectory(project);

    final String launchingScript = parameters.getLaunchingScript();
    assert launchingScript != null;
    final String bazelTarget = parameters.getBazelTarget();
    assert bazelTarget != null;

    final FlutterDaemonService service = FlutterDaemonService.getInstance(getEnvironment().getProject());
    myApp = service.startBazelApp(
      project,
      cwd,
      launchingScript,
      device,
      myMode,
      bazelTarget,
      parameters.getAdditionalArgs());
    return myApp.getController().getProcessHandler();
  }
}

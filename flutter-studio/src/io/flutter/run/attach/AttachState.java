/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.run.LaunchState;
import io.flutter.run.daemon.DeviceService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import org.jetbrains.annotations.NotNull;

public class AttachState extends LaunchState {

  public AttachState(@NotNull ExecutionEnvironment env,
                     @NotNull VirtualFile workDir,
                     @NotNull VirtualFile sourceLocation,
                     @NotNull RunConfig runConfig,
                     @NotNull CreateAppCallback createAppCallback) {
    super(env, workDir, sourceLocation, runConfig, createAppCallback);
  }

  @Override
  protected RunContentDescriptor launch(@NotNull ExecutionEnvironment env) throws ExecutionException {
    Project project = getEnvironment().getProject();
    FlutterDevice device = DeviceService.getInstance(project).getSelectedDevice();
    if (device == null) {
      showNoDeviceConnectedMessage(project);
      return null;
    }
    FlutterApp app = getCreateAppCallback().createApp(device);
    // Cache for use in console configuration.
    FlutterApp.addToEnvironment(env, app);
    ExecutionResult result = setUpConsoleAndActions(app);
    return createDebugSession(env, app, result).getRunContentDescriptor();
  }
}

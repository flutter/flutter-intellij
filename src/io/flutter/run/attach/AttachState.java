/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfileState;
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
                     @NotNull Callback callback) {
    super(env, workDir, sourceLocation, runConfig, callback);
  }

  @Override
  protected RunContentDescriptor launch(@NotNull ExecutionEnvironment env) throws ExecutionException {
    Project project = getEnvironment().getProject();
    FlutterDevice device = DeviceService.getInstance(project).getSelectedDevice();
    FlutterApp app = getCallback().createApp(device);
    ExecutionResult result = setUpConsoleAndActions(app);
    return createDebugSession(env, app, result).getRunContentDescriptor();
  }

  public static abstract class Attacher<C extends RunConfig> extends LaunchState.Runner<SdkAttachConfig> {

    public Attacher(Class<AttachState> runConfigClass) {
      super(SdkAttachConfig.class);
    }

    @Override
    protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env)
      throws ExecutionException {
      AttachState launchState = (AttachState)state;
      return launchState.launch(env);
    }
  }
}

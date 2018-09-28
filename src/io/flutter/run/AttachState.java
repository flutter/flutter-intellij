/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.run;

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
    // Cache for use in console configuration, and for updating registered extensionRPCs.
    FlutterApp.addToEnvironment(env, app);
    ExecutionResult result = setUpConsoleAndActions(app);
    return createDebugSession(env, app, result).getRunContentDescriptor();
  }
}

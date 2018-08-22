/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.attach;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.run.LaunchState;
import org.jetbrains.annotations.NotNull;

public class AttachState extends LaunchState {

  public AttachState(@NotNull ExecutionEnvironment env,
                     @NotNull VirtualFile workDir,
                     @NotNull VirtualFile sourceLocation,
                     @NotNull RunConfig runConfig,
                     @NotNull Callback callback) {
    super(env, workDir, sourceLocation, runConfig, callback);
  }
}

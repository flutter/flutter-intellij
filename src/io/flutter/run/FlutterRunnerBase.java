/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;


import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is copied from the Dart plugin and modified to use DartVmServiceDebugProcessZ to control the debugger, and to define
 * ObservatoryConnector.
 */
abstract class FlutterRunnerBase extends DefaultProgramRunner {

  @NotNull
  @Override
  public String getRunnerId() {
    return "DartRunner";
  }

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
    return (profile instanceof FlutterRunConfiguration && (DefaultRunExecutor.EXECUTOR_ID.equals(executorId) ||
                                                           DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)));
  }

  protected int getTimeout() {
    return 5000; // Allow 5 seconds to connect to the observatory.
  }

  @Nullable
  protected ObservatoryConnector getConnector() {
    return null;
  }

  DartUrlResolver getDartUrlResolver(@NotNull final Project project, @NotNull final VirtualFile contextFileOrDir) {
    return DartUrlResolver.getInstance(project, contextFileOrDir);
  }
}

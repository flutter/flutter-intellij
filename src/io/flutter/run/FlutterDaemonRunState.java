/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterDaemonRunState extends FlutterRunningState {
  // TODO Remove FlutterRunningState
  private DaemonManager daemon;

  public FlutterDaemonRunState(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    super(environment);
  }

  public DaemonManager getDaemon() {
    return daemon;
  }

  public int getObservatoryPort() {
    if (isConnectionReady()) {
      return daemon.getPort(this);
    }
    else {
      // TODO Eliminate this branch
      return super.getObservatoryPort();
    }
  }

  protected ProcessHandler doStartProcess(final @Nullable String overriddenMainFilePath) throws ExecutionException {
    ProcessHandler handler = super.doStartProcess(overriddenMainFilePath);
    daemon = new DaemonManager(getEnvironment().getProject());
    handler.addProcessListener(daemon);
    return handler;
  }

  String startUpCommand() {
    return "daemon";
  }

  boolean isConnectionReady() {
    return daemon.isConnectionReady(this);
  }
}

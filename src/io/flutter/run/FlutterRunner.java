/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDaemonService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterRunner extends FlutterRunnerBase {

  @Nullable
  private ObservatoryConnector myConnector;

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterRunner";
  }

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
    FlutterDaemonService service = FlutterDaemonService.getInstance();
    return (profile instanceof FlutterRunConfiguration &&
            (DefaultRunExecutor.EXECUTOR_ID.equals(executorId) || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId))) &&
           (service != null && service.getSelectedDevice() != null);
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    if (state instanceof FlutterAppState) {
      final FlutterAppState appState = (FlutterAppState)state;
      myConnector = new ObservatoryConnector() {
        @Override
        public boolean isConnectionReady() {
          return appState.isConnectionReady();
        }

        @Override
        public int getPort() {
          return appState.getObservatoryPort();
        }

        @Override
        public FlutterApp getApp() {
          return appState.getApp();
        }

        @Override
        public void sessionPaused(XDebugSession sessionHook) {
          appState.getApp().sessionPaused(sessionHook);
        }

        @Override
        public void sessionResumed() {
          appState.getApp().sessionResumed();
        }
      };
    }
    return super.doExecute(state, env);
  }

  @Override
  protected int getTimeout() {
    return 90000; // Allow 90 seconds to connect to the observatory.
  }

  @Nullable
  protected ObservatoryConnector getConnector() {
    return myConnector;
  }
}

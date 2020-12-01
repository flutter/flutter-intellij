/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.ObservatoryConnector;
import io.flutter.run.FlutterPopFrameAction;
import io.flutter.run.OpenDevToolsAction;
import io.flutter.vmService.DartVmServiceDebugProcess;
import org.jetbrains.annotations.NotNull;

/**
 * The Bazel version of the {@link io.flutter.run.test.TestDebugProcess}.
 */
public class BazelTestDebugProcess extends DartVmServiceDebugProcess {
  @NotNull
  private final ObservatoryConnector connector;

  public BazelTestDebugProcess(@NotNull ExecutionEnvironment executionEnvironment,
                               @NotNull XDebugSession session,
                               @NotNull ExecutionResult executionResult,
                               @NotNull DartUrlResolver dartUrlResolver,
                               @NotNull ObservatoryConnector connector,
                               @NotNull PositionMapper mapper) {
    super(executionEnvironment, session, executionResult, dartUrlResolver, connector, mapper);
    this.connector = connector;
  }

  @Override
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar,
                                        @NotNull DefaultActionGroup topToolbar,
                                        @NotNull DefaultActionGroup settings) {
    topToolbar.addSeparator();
    topToolbar.addAction(new FlutterPopFrameAction());
  }

  private boolean isActive() {
    return connector.getBrowserUrl() != null && getVmConnected() && !getSession().isStopped();
  }
}

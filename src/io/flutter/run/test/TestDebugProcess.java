/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import io.flutter.server.vmService.DartVmServiceDebugProcessZ;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.run.FlutterPopFrameAction;
import io.flutter.run.OpenObservatoryAction;
import org.jetbrains.annotations.NotNull;

/**
 * A debug process used when debugging a Flutter test.
 */
public class TestDebugProcess extends DartVmServiceDebugProcessZ {
  @NotNull
  private final ObservatoryConnector connector;

  public TestDebugProcess(@NotNull ExecutionEnvironment executionEnvironment,
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
    topToolbar.addAction(new OpenObservatoryAction(connector, this::isActive));
  }

  private boolean isActive() {
    return connector.getBrowserUrl() != null && getVmConnected() && !getSession().isStopped();
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcessZ;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class FlutterDebugProcess extends DartVmServiceDebugProcessZ {

  private static final Logger LOG = Logger.getInstance(FlutterDebugProcess.class.getName());
  @NotNull private final RunProfileState myState;

  public FlutterDebugProcess(@NotNull XDebugSession session,
                             @NotNull String debuggingHost,
                             int observatoryPort,
                             @NotNull RunProfileState state,
                             @Nullable ExecutionResult executionResult,
                             @NotNull DartUrlResolver dartUrlResolver,
                             @Nullable String dasExecutionContextId,
                             boolean remoteDebug,
                             int timeout,
                             @Nullable VirtualFile currentWorkingDirectory,
                             @Nullable ObservatoryConnector connector) {
    super(session, debuggingHost, observatoryPort, executionResult, dartUrlResolver, dasExecutionContextId, remoteDebug, timeout,
          currentWorkingDirectory, connector);
    myState = state;
  }

  /**
   * Test if the given run profile state should be run as a debugging session.
   *
   * @param state the state to check
   * @return true if the profile should be run as a debug session, false otherwise
   */
  public static boolean isDebuggingSession(RunProfileState state) {
    return (state instanceof FlutterAppState) && ((FlutterAppState)state).getMode() == RunMode.DEBUG;
  }

  @Override
  public boolean shouldEnableHotReload() {
    return ((FlutterAppState)myState).getMode().isReloadEnabled();
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean isDebuggingSession() {
    return isDebuggingSession(myState);
  }

  @Override
  public void registerAdditionalActions(@NotNull final DefaultActionGroup leftToolbar,
                                        @NotNull final DefaultActionGroup topToolbar,
                                        @NotNull final DefaultActionGroup settings) {

    if (!isDebuggingSession()) {

      // Remove all but specified actions.
      final AnAction[] leftActions = leftToolbar.getChildActionsOrStubs();
      // Not all on the classpath so we resort to Strings.
      final List<String> actionClassNames = Arrays
        .asList("com.intellij.execution.actions.StopAction", "com.intellij.ui.content.tabs.PinToolwindowTabAction",
                "com.intellij.execution.ui.actions.CloseAction", "com.intellij.ide.actions.ContextHelpAction");
      for (AnAction a : leftActions) {
        if (!actionClassNames.contains(a.getClass().getName())) {
          leftToolbar.remove(a);
        }
      }

      // Remove all top actions.
      final AnAction[] topActions = topToolbar.getChildActionsOrStubs();
      for (AnAction action : topActions) {
        topToolbar.remove(action);
      }

      // Remove all settings actions.
      final AnAction[] settingsActions = settings.getChildActionsOrStubs();
      for (AnAction a : settingsActions) {
        settings.remove(a);
      }
    }

    super.registerAdditionalActions(leftToolbar, topToolbar, settings);
  }

  @Override
  public void sessionInitialized() {
    // If running outside a Debug launch, suppress debug views (e.g., variables and frames).
    if (!isDebuggingSession()) {
      final RunnerLayoutUi ui = getSession().getUI();
      if (ui != null) {
        for (Content c : ui.getContents()) {
          if (!Objects.equals(c.getTabName(), "Console")) {
            try {
              GuiUtils.runOrInvokeAndWait(() -> ui.removeContent(c, false /* dispose? */));
            }
            catch (InvocationTargetException | InterruptedException e) {
              LOG.warn(e);
            }
          }
        }
      }
    }
  }
}

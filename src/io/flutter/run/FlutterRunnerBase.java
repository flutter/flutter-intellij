/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;


import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcessZ;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is copied from the Dart plugin and modified to use DartVmServiceDebugProcessZ to control the debugger, and to define
 * ObservatoryConnector.
 */
public class FlutterRunnerBase extends DefaultProgramRunner {
  private static final Logger LOG = Logger.getInstance(FlutterRunnerBase.class.getName());

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

  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final String executorId = env.getExecutor().getId();

    if (DefaultRunExecutor.EXECUTOR_ID.equals(executorId)) {
      return super.doExecute(state, env);
    }

    if (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
      try {
        final String dasExecutionContextId;

        final RunProfile runConfig = env.getRunProfile();
        if (runConfig instanceof FlutterRunConfigurationBase &&
            DartAnalysisServerService.getInstance().serverReadyForRequest(env.getProject())) {
          final String path = ((FlutterRunConfigurationBase)runConfig).getRunnerParameters().getFilePath();
          assert path != null; // already checked
          dasExecutionContextId = DartAnalysisServerService.getInstance().execution_createContext(path);
        }
        else {
          dasExecutionContextId = null; // remote debug or can't start DAS
        }

        return doExecuteDartDebug(state, env, dasExecutionContextId);
      }
      catch (RuntimeConfigurationError e) {
        throw new ExecutionException(e);
      }
    }

    LOG.error("Unexpected executor id: " + executorId);
    return null;
  }

  protected int getTimeout() {
    return 5000; // Allow 5 seconds to connect to the observatory.
  }

  @Nullable
  protected ObservatoryConnector getConnector() {
    return null;
  }

  private RunContentDescriptor doExecuteDartDebug(final @NotNull RunProfileState state,
                                                  final @NotNull ExecutionEnvironment env,
                                                  final @Nullable String dasExecutionContextId) throws RuntimeConfigurationError,
                                                                                                       ExecutionException {
    final DartSdk sdk = DartSdk.getDartSdk(env.getProject());
    assert (sdk != null); // already checked

    final RunProfile runConfiguration = env.getRunProfile();
    final VirtualFile contextFileOrDir;
    VirtualFile currentWorkingDirectory;
    final ExecutionResult executionResult;
    final String debuggingHost;
    final int observatoryPort;

    if (runConfiguration instanceof FlutterRunConfigurationBase) {
      contextFileOrDir = ((FlutterRunConfigurationBase)runConfiguration).getRunnerParameters().getDartFile();

      final String cwd =
        ((FlutterRunConfigurationBase)runConfiguration).getRunnerParameters().computeProcessWorkingDirectory(env.getProject());
      currentWorkingDirectory = LocalFileSystem.getInstance().findFileByPath((cwd));

      executionResult = state.execute(env.getExecutor(), this);
      if (executionResult == null) {
        return null;
      }

      debuggingHost = null;
      observatoryPort = ((FlutterAppState)state).getObservatoryPort();
    }
    else {
      LOG.error("Unexpected run configuration: " + runConfiguration.getClass().getName());
      return null;
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    final XDebuggerManager debuggerManager = XDebuggerManager.getInstance(env.getProject());
    final XDebugSession debugSession = debuggerManager.startSession(env, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull final XDebugSession session) {
        final DartUrlResolver dartUrlResolver = getDartUrlResolver(env.getProject(), contextFileOrDir);
        return new DartVmServiceDebugProcessZ(session,
                                              StringUtil.notNullize(debuggingHost, "localhost"),
                                              observatoryPort,
                                              executionResult,
                                              dartUrlResolver,
                                              dasExecutionContextId,
                                              false,
                                              getTimeout(),
                                              currentWorkingDirectory,
                                              getConnector());
      }
    });

    return debugSession.getRunContentDescriptor();
  }

  protected DartUrlResolver getDartUrlResolver(@NotNull final Project project, @NotNull final VirtualFile contextFileOrDir) {
    return DartUrlResolver.getInstance(project, contextFileOrDir);
  }
}

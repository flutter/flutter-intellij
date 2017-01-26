/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.dart.DartPlugin;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDaemonService;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterRunner extends FlutterRunnerBase {
  private static final Key<RunConfiguration> RUN_CONFIGURATION_KEY = new Key<>("RUN_CONFIGURATION_KEY");

  private static final Logger LOG = Logger.getInstance(FlutterRunner.class.getName());

  @Nullable
  private ObservatoryConnector myConnector;

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterRunner";
  }

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
    if (!(profile instanceof FlutterRunConfiguration)) {
      return false;
    }

    final FlutterRunConfigurationBase runConfiguration = (FlutterRunConfigurationBase)profile;
    final Project project = runConfiguration.getProject();

    if (FlutterSdk.getFlutterSdk(project) == null) {
      return false;
    }

    final FlutterDaemonService service = FlutterDaemonService.getInstance(project);
    //noinspection SimplifiableIfStatement
    if (!service.isActive() || !service.hasSelectedDevice()) {
      return false;
    }

    // Check to see if there are any processes currently running that were launched from this config.
    //noinspection SimplifiableIfStatement
    if (hasAnyRunningConfigs(runConfiguration)) {
      return false;
    }

    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId) || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId);
  }

  protected boolean hasAnyRunningConfigs(FlutterRunConfigurationBase runConfiguration) {
    final List<RunContentDescriptor> runningProcesses =
      ExecutionManager.getInstance(runConfiguration.getProject()).getContentManager().getAllDescriptors();

    for (RunContentDescriptor descriptor : runningProcesses) {
      final ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler != null) {
        if (!processHandler.isProcessTerminated()) {
          final RunConfiguration descriptorRunConfig = processHandler.getUserData(RUN_CONFIGURATION_KEY);
          if (descriptorRunConfig == runConfiguration) {
            return true;
          }
        }
      }
    }

    return false;
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
        @Nullable
        public String getObservatoryWsUrl() {
          return appState.getWsUrl();
        }

        @Override
        @NotNull
        public FlutterApp getApp() {
          final FlutterApp app = appState.getApp();
          assert app != null;
          return app;
        }

        @Override
        public void sessionPaused(XDebugSession sessionHook) {
          getApp().sessionPaused(sessionHook);
        }

        @Override
        public void sessionResumed() {
          getApp().sessionResumed();
        }
      };
    }

    String dasExecutionContextId = null;
    final RunProfile runConfig = env.getRunProfile();

    if (runConfig instanceof FlutterRunConfigurationBase &&
        DartAnalysisServerService.getInstance().serverReadyForRequest(env.getProject())) {
      try {
        final VirtualFile file = ((FlutterRunConfigurationBase)runConfig).getRunnerParameters().getBestContextFile();
        final String path = file.getPath();
        dasExecutionContextId = DartAnalysisServerService.getInstance().execution_createContext(path);
      }
      catch (RuntimeConfigurationError ignore) {
      }
    }

    try {
      if (state instanceof FlutterAppState && ((FlutterAppState)state).getMode().isReloadEnabled()) {
        return doExecuteDartDebug(state, env, dasExecutionContextId);
      }
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    return super.doExecute(state, env);
  }

  private RunContentDescriptor doExecuteDartDebug(final @NotNull RunProfileState state,
                                                  final @NotNull ExecutionEnvironment env,
                                                  final @Nullable String dasExecutionContextId) throws RuntimeConfigurationError,
                                                                                                       ExecutionException {
    final DartSdk sdk = DartPlugin.getDartSdk(env.getProject());
    assert (sdk != null); // already checked

    final RunProfile runConfiguration = env.getRunProfile();
    if (!(runConfiguration instanceof FlutterRunConfigurationBase)) {
      LOG.error("Unexpected run configuration: " + runConfiguration.getClass().getName());
      return null;
    }

    if (!(state instanceof FlutterAppState)) {
      LOG.error("Unexpected run profile state: " + state.getClass().getName());
      return null;
    }

    final FlutterAppState appState = (FlutterAppState)state;
    final VirtualFile contextFileOrDir;
    final VirtualFile currentWorkingDirectory;
    final ExecutionResult executionResult;
    final String debuggingHost;
    final int observatoryPort;

    final FlutterRunConfigurationBase flutterRunConfig = (FlutterRunConfigurationBase)runConfiguration;

    contextFileOrDir = flutterRunConfig.getRunnerParameters().getBestContextFile();

    final String cwd = flutterRunConfig.getRunnerParameters().computeProcessWorkingDirectory(env.getProject());
    currentWorkingDirectory = LocalFileSystem.getInstance().findFileByPath((cwd));

    executionResult = appState.execute(env.getExecutor(), this);

    FileDocumentManager.getInstance().saveAllDocuments();

    final XDebuggerManager debuggerManager = XDebuggerManager.getInstance(env.getProject());
    final XDebugSession debugSession = debuggerManager.startSession(env, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull final XDebugSession session) {
        final DartUrlResolver dartUrlResolver = getDartUrlResolver(env.getProject(), contextFileOrDir);
        final ObservatoryConnector observatoryConnector = getConnector();
        assert (observatoryConnector != null);
        return new FlutterDebugProcess(session,
                                       state,
                                       executionResult,
                                       dartUrlResolver,
                                       dasExecutionContextId,
                                       false,
                                       getTimeout(),
                                       currentWorkingDirectory,
                                       observatoryConnector);
      }
    });

    if (!FlutterDebugProcess.isDebuggingSession(appState)) {
      debugSession.setBreakpointMuted(true);
    }

    // Store the run configuration so we know which config started this process.
    final ProcessHandler processHandler = debugSession.getRunContentDescriptor().getProcessHandler();
    if (processHandler != null) {
      processHandler.putUserData(RUN_CONFIGURATION_KEY, flutterRunConfig);
    }

    return debugSession.getRunContentDescriptor();
  }

  @Override
  protected int getTimeout() {
    // Allow 5 minutes to connect to the observatory; the user can cancel manually in the interim.
    return 5 * 60 * 1000;
  }

  @Nullable
  protected ObservatoryConnector getConnector() {
    return myConnector;
  }
}

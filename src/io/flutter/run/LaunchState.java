/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.*;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.actions.OpenSimulatorAction;
import io.flutter.dart.DartPlugin;
import io.flutter.run.daemon.*;
import io.flutter.view.OpenFlutterViewAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Launches a flutter app, showing it in the console.
 * <p>
 * Normally creates a debugging session, which is needed for hot reload.
 */
public class LaunchState extends CommandLineState {
  private final @NotNull VirtualFile workDir;

  /**
   * The file or directory holding the Flutter app's source code.
   * This determines how the analysis server resolves URI's (for breakpoints, etc).
   * <p>
   * If a file, this should be the file containing the main() method.
   */
  private final @NotNull VirtualFile sourceLocation;

  private final @NotNull RunConfig runConfig;
  private final @NotNull Callback callback;

  public LaunchState(@NotNull ExecutionEnvironment env,
                     @NotNull VirtualFile workDir,
                     @NotNull VirtualFile sourceLocation,
                     @NotNull RunConfig runConfig,
                     @NotNull Callback callback) {
    super(env);
    this.workDir = workDir;
    this.sourceLocation = sourceLocation;
    this.runConfig = runConfig;
    this.callback = callback;
    DaemonConsoleView.install(this, env, workDir);
  }

  private RunContentDescriptor launch(@NotNull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    final Project project = getEnvironment().getProject();
    final FlutterDevice device = DeviceService.getInstance(project).getSelectedDevice();
    final FlutterApp app = callback.createApp(device);

    if (device == null) {
      Messages.showDialog(
        project,
        "No connected devices found; please connect a device, or see flutter.io/setup for getting started instructions.",
        "No Connected Devices Found",
        new String[]{Messages.OK_BUTTON}, 0, AllIcons.General.InformationDialog);

      return null;
    }

    // Remember the run configuration that started this process.
    app.getProcessHandler().putUserData(FLUTTER_RUN_CONFIG_KEY, runConfig);
    assert (app.getMode().mode().equals(getEnvironment().getExecutor().getId()));

    final ExecutionResult result = setUpConsoleAndActions(app);

    if (device.emulator() && device.isIOS()) {
      // Bring simulator to front.
      new OpenSimulatorAction(true).actionPerformed(null);
    }

    // Check for and display any analysis errors when we launch an app.
    if (env.getRunProfile() instanceof SdkRunConfig) {
      final Class dartExecutionHelper = classForName("com.jetbrains.lang.dart.ide.runner.DartExecutionHelper");
      if (dartExecutionHelper != null) {
        final String message = ("<a href='open.dart.analysis'>Analysis issues</a> may affect " +
                                "the execution of '" + env.getRunProfile().getName() + "'.");
        final SdkRunConfig config = (SdkRunConfig)env.getRunProfile();
        final SdkFields sdkFields = config.getFields();
        final MainFile mainFile = MainFile.verify(sdkFields.getFilePath(), env.getProject()).get();

        // TODO(devoncarew): Remove the use of reflection when we rev our min version to 2017.2.
        //DartExecutionHelper.displayIssues(project, mainFile.getFile(), message, env.getRunProfile().getIcon());
        try {
          final Method displayIssues =
            ReflectionUtil.getMethod(dartExecutionHelper, "displayIssues", Project.class, VirtualFile.class, String.class, Icon.class);
          if (displayIssues != null) {
            displayIssues.invoke(null, project, mainFile.getFile(), message, env.getRunProfile().getIcon());
          }
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          // ignore
        }
      }
    }

    final FlutterLaunchMode launchMode = FlutterLaunchMode.getMode(env);
    if (launchMode.supportsReload()) {
      return createDebugSession(env, app, result).getRunContentDescriptor();
    }
    else {
      // Not used yet. See https://github.com/flutter/flutter-intellij/issues/410
      return new RunContentBuilder(result, env).showRunContent(env.getContentToReuse());
    }
  }

  private static Class classForName(String className) {
    try {
      return Class.forName(className);
    }
    catch (ClassNotFoundException e) {
      return null;
    }
  }

  @NotNull
  private XDebugSession createDebugSession(@NotNull final ExecutionEnvironment env,
                                           @NotNull final FlutterApp app,
                                           @NotNull final ExecutionResult executionResult)
    throws ExecutionException {

    final DartUrlResolver resolver = DartUrlResolver.getInstance(env.getProject(), sourceLocation);
    final PositionMapper mapper = createPositionMapper(env, app, resolver);

    final XDebuggerManager manager = XDebuggerManager.getInstance(env.getProject());
    final XDebugSession session = manager.startSession(env, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull final XDebugSession session) {

        return new FlutterDebugProcess(app, env, session, executionResult, resolver, mapper);
      }
    });

    if (app.getMode() != RunMode.DEBUG) {
      session.setBreakpointMuted(true);
    }

    return session;
  }

  @NotNull
  private PositionMapper createPositionMapper(@NotNull ExecutionEnvironment env,
                                              @NotNull FlutterApp app,
                                              @NotNull DartUrlResolver resolver) {
    final PositionMapper.Analyzer analyzer;
    if (app.getMode() == RunMode.DEBUG) {
      analyzer = PositionMapper.Analyzer.create(env.getProject(), sourceLocation);
    }
    else {
      analyzer = null; // Don't need analysis server just to run.
    }

    // Choose source root containing the Dart application.
    // TODO(skybrian) for bazel, we probably should pass in three source roots here (for bazel-bin, bazel-genfiles, etc).
    final VirtualFile pubspec = resolver.getPubspecYamlFile();
    final VirtualFile sourceRoot = pubspec != null ? pubspec.getParent() : workDir;

    return new PositionMapper(env.getProject(), sourceRoot, resolver, analyzer);
  }

  @NotNull
  private ExecutionResult setUpConsoleAndActions(@NotNull FlutterApp app) throws ExecutionException {
    final ConsoleView console = createConsole(getEnvironment().getExecutor());
    if (console != null) {
      app.setConsole(console);
      console.attachToProcess(app.getProcessHandler());
    }

    // Add observatory actions.
    // These actions are effectively added only to the Run tool window.
    // For Debug see FlutterDebugProcess.registerAdditionalActions()
    final List<AnAction> actions = new ArrayList<>(Arrays.asList(
      super.createActions(console, app.getProcessHandler(), getEnvironment().getExecutor())));
    actions.add(new Separator());
    actions.add(new OpenFlutterViewAction(() -> !app.getProcessHandler().isProcessTerminated()));
    actions.add(new OpenObservatoryAction(app.getConnector(), () -> !app.getProcessHandler().isProcessTerminated() &&
                                                                    app.getConnector().getBrowserUrl() != null));

    return new DefaultExecutionResult(console, app.getProcessHandler(), actions.toArray(new AnAction[actions.size()]));
  }

  @Override
  public @NotNull
  ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    throw new ExecutionException("not implemented"); // Not used; launch() does this.
  }

  @Override
  protected @NotNull
  ProcessHandler startProcess() throws ExecutionException {
    // This can happen if there isn't a custom runner defined in plugin.xml.
    // The runner should extend LaunchState.Runner (below).
    throw new ExecutionException("need to implement LaunchState.Runner for " + runConfig.getClass());
  }

  /**
   * Starts the process and wraps it in a FlutterApp.
   * <p>
   * The callback knows the appropriate command line arguments (bazel versus non-bazel).
   */
  public interface Callback {
    FlutterApp createApp(@Nullable FlutterDevice device) throws ExecutionException;
  }

  /**
   * A run configuration that works with Launcher.
   */
  public interface RunConfig extends RunProfile {
    Project getProject();

    @NotNull
    LaunchState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException;
  }

  /**
   * A runner that automatically invokes {@link #launch}.
   */
  public static abstract class Runner<C extends RunConfig> extends GenericProgramRunner {
    private final Class<C> runConfigClass;

    public Runner(Class<C> runConfigClass) {
      this.runConfigClass = runConfigClass;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public final boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
      if (!DefaultRunExecutor.EXECUTOR_ID.equals(executorId) && !DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
        return false;
      }

      if (!(profile instanceof RunConfig)) {
        return false;
      }

      // If the app is running and the launch mode is the same, then we can run.
      final RunConfig config = (RunConfig)profile;
      final ProcessHandler process = getRunningAppProcess(config);
      if (process != null) {
        final FlutterApp app = FlutterApp.fromProcess(process);
        if (app == null) {
          return false;
        }

        final String selectedDeviceId = getSelectedDeviceId(config.getProject());

        // Only continue checks for this app if the launched device is the same as the selected one.
        if (StringUtil.equals(app.deviceId(), selectedDeviceId)) {
          // Disable if no app or this isn't the mode that app was launched in.
          if (!executorId.equals(app.getMode().mode())) {
            return false;
          }

          // Disable the run/debug buttons if the app is starting up.
          if (app.getState() == FlutterApp.State.STARTING || app.getState() == FlutterApp.State.RELOADING) {
            return false;
          }
        }
      }

      if (DartPlugin.getDartSdk(config.getProject()) == null) {
        return false;
      }

      return runConfigClass.isInstance(profile) && canRun(runConfigClass.cast(profile));
    }

    /**
     * Subclass hook for additional checks.
     */
    protected boolean canRun(C config) {
      return true;
    }

    @Override
    protected final RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env)
      throws ExecutionException {
      if (!(state instanceof LaunchState)) {
        LOG.error("unexpected RunProfileState: " + state.getClass());
        return null;
      }

      final LaunchState launchState = (LaunchState)state;
      final String executorId = env.getExecutor().getId();

      // See if we should issue a hot-reload.
      final List<RunContentDescriptor> runningProcesses =
        ExecutionManager.getInstance(env.getProject()).getContentManager().getAllDescriptors();

      final ProcessHandler process = getRunningAppProcess(launchState.runConfig);
      if (process != null) {
        final FlutterApp app = FlutterApp.fromProcess(process);
        final String selectedDeviceId = getSelectedDeviceId(env.getProject());

        if (app != null && StringUtil.equals(app.deviceId(), selectedDeviceId)) {
          if (executorId.equals(app.getMode().mode())) {
            final FlutterLaunchMode launchMode = FlutterLaunchMode.getMode(env);
            if (launchMode.supportsReload() && app.isStarted()) {
              // Map a re-run action to a flutter full restart.
              FileDocumentManager.getInstance().saveAllDocuments();
              app.performRestartApp();
            }
          }

          return null;
        }
      }

      // Else, launch the app.
      return launchState.launch(env);
    }

    @Nullable
    private String getSelectedDeviceId(@NotNull Project project) {
      final FlutterDevice selectedDevice = DeviceService.getInstance(project).getSelectedDevice();
      return selectedDevice == null ? null : selectedDevice.deviceId();
    }
  }

  /**
   * Returns the currently running app for the given RunConfig, if any.
   */
  @Nullable
  public static ProcessHandler getRunningAppProcess(RunConfig config) {
    final Project project = config.getProject();
    final List<RunContentDescriptor> runningProcesses =
      ExecutionManager.getInstance(project).getContentManager().getAllDescriptors();

    for (RunContentDescriptor descriptor : runningProcesses) {
      final ProcessHandler process = descriptor.getProcessHandler();
      if (process != null && !process.isProcessTerminated() && process.getUserData(FLUTTER_RUN_CONFIG_KEY) == config) {
        return process;
      }
    }

    return null;
  }

  private static final Key<RunConfig> FLUTTER_RUN_CONFIG_KEY = new Key<>("FLUTTER_RUN_CONFIG_KEY");

  private static final Logger LOG = Logger.getInstance(LaunchState.class.getName());
}

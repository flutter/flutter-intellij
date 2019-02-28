/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.*;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.lang.dart.ide.runner.DartExecutionHelper;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.actions.RestartFlutterApp;
import io.flutter.dart.DartPlugin;
import io.flutter.logging.FlutterLog;
import io.flutter.logging.FlutterLogView;
import io.flutter.run.bazel.BazelRunConfig;
import io.flutter.run.daemon.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Launches a flutter app, showing it in the console.
 * <p>
 * Normally creates a debugging session, which is needed for hot reload.
 */
public class LaunchState extends CommandLineState {
  // We use the profile launch type, contributed by the Android IntelliJ plugins
  // in 2017.3 and Android Studio 3.0, if it's available. This allows us to support
  // their 'profile' launch button, next to the regular run and debug ones.
  public static final String ANDROID_PROFILER_EXECUTOR_ID = "Android Profiler";

  private final @NotNull VirtualFile workDir;

  /**
   * The file or directory holding the Flutter app's source code.
   * This determines how the analysis server resolves URI's (for breakpoints, etc).
   * <p>
   * If a file, this should be the file containing the main() method.
   */
  private final @NotNull VirtualFile sourceLocation;

  private final @NotNull RunConfig runConfig;
  private final @NotNull CreateAppCallback myCreateAppCallback;

  public LaunchState(@NotNull ExecutionEnvironment env,
                     @NotNull VirtualFile workDir,
                     @NotNull VirtualFile sourceLocation,
                     @NotNull RunConfig runConfig,
                     @NotNull CreateAppCallback createAppCallback) {
    super(env);
    this.workDir = workDir;
    this.sourceLocation = sourceLocation;
    this.runConfig = runConfig;
    this.myCreateAppCallback = createAppCallback;
    DaemonConsoleView.install(this, env, workDir);
  }

  @NotNull
  protected CreateAppCallback getCreateAppCallback() {
    return myCreateAppCallback;
  }

  @Override
  @Nullable
  protected ConsoleView createConsole(@NotNull final Executor executor) throws ExecutionException {
    if (FlutterLog.isLoggingEnabled()) {
      final FlutterApp app = FlutterApp.fromEnv(getEnvironment());
      assert app != null;
      return new FlutterLogView(app);
    }
    else {
      return super.createConsole(executor);
    }
  }

  protected RunContentDescriptor launch(@NotNull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    // Set our FlutterLaunchMode up in the ExecutionEnvironment.
    if (RunMode.fromEnv(env).isProfiling()) {
      FlutterLaunchMode.addToEnvironment(env, FlutterLaunchMode.PROFILE);
    }

    final Project project = getEnvironment().getProject();
    final FlutterDevice device = DeviceService.getInstance(project).getSelectedDevice();

    if (device == null) {
      showNoDeviceConnectedMessage(project);
      return null;
    }
    final FlutterApp app = myCreateAppCallback.createApp(device);

    // Cache for use in console configuration.
    FlutterApp.addToEnvironment(env, app);

    // Remember the run configuration that started this process.
    app.getProcessHandler().putUserData(FLUTTER_RUN_CONFIG_KEY, runConfig);

    final ExecutionResult result = setUpConsoleAndActions(app);

    // For Bazel run configurations,
    // where the console is not null,
    // and we find the expected process handler type,
    // print the command line command to the console.
    if (runConfig instanceof BazelRunConfig &&
        app.getConsole() != null &&
        app.getProcessHandler() instanceof OSProcessHandler) {
      final String commandLineString = ((OSProcessHandler)app.getProcessHandler()).getCommandLine().trim();
      if (StringUtil.isNotEmpty(commandLineString)) {
        app.getConsole().print(commandLineString + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
      }
    }

    device.bringToFront();

    // Check for and display any analysis errors when we launch an app.
    if (env.getRunProfile() instanceof SdkRunConfig) {
      final Class dartExecutionHelper = classForName("com.jetbrains.lang.dart.ide.runner.DartExecutionHelper");
      if (dartExecutionHelper != null) {
        final String message = ("<a href='open.dart.analysis'>Analysis issues</a> may affect " +
                                "the execution of '" + env.getRunProfile().getName() + "'.");
        final SdkRunConfig config = (SdkRunConfig)env.getRunProfile();
        final SdkFields sdkFields = config.getFields();
        final MainFile mainFile = MainFile.verify(sdkFields.getFilePath(), env.getProject()).get();

        DartExecutionHelper.displayIssues(project, mainFile.getFile(), message, env.getRunProfile().getIcon());
      }
    }

    final FlutterLaunchMode launchMode = FlutterLaunchMode.fromEnv(env);
    if (launchMode.supportsDebugConnection()) {
      return createDebugSession(env, app, result).getRunContentDescriptor();
    }
    else {
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

  protected void showNoDeviceConnectedMessage(Project project) {
    Messages.showDialog(
      project,
      "No connected devices found; please connect a device, or see flutter.io/setup for getting started instructions.",
      "No Connected Devices Found",
      new String[]{Messages.OK_BUTTON}, 0, AllIcons.General.InformationDialog);
  }

  @NotNull
  protected XDebugSession createDebugSession(@NotNull final ExecutionEnvironment env,
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
  protected ExecutionResult setUpConsoleAndActions(@NotNull FlutterApp app) throws ExecutionException {
    final ConsoleView console = createConsole(getEnvironment().getExecutor());
    if (console != null) {
      app.setConsole(console);
      console.attachToProcess(app.getProcessHandler());
    }

    // Add observatory actions.
    // These actions are effectively added only to the Run tool window.
    // For Debug see FlutterDebugProcess.registerAdditionalActions()
    final Computable<Boolean> observatoryAvailable = () -> !app.getProcessHandler().isProcessTerminated() &&
                                                           app.getConnector().getBrowserUrl() != null;
    final List<AnAction> actions = new ArrayList<>(Arrays.asList(
      super.createActions(console, app.getProcessHandler(), getEnvironment().getExecutor())));
    actions.add(new Separator());
    actions.add(new OpenDevToolsAction(app.getConnector(), observatoryAvailable));
    actions.add(new Separator());
    actions.add(new OpenObservatoryAction(app.getConnector(), observatoryAvailable));
    actions.add(new OpenTimelineViewAction(app.getConnector(), observatoryAvailable));

    return new DefaultExecutionResult(console, app.getProcessHandler(), actions.toArray(new AnAction[0]));
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
  public interface CreateAppCallback {
    FlutterApp createApp(@Nullable FlutterDevice device) throws ExecutionException;
  }

  /**
   * A run configuration that works with Launcher.
   */
  public interface RunConfig extends RunProfile {
    Project getProject();

    @Override
    @NotNull
    LaunchState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException;

    @NotNull
    GeneralCommandLine getCommand(ExecutionEnvironment environment, FlutterDevice device) throws ExecutionException;
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
    public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
      if (!DefaultRunExecutor.EXECUTOR_ID.equals(executorId) &&
          !DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) &&
          !ANDROID_PROFILER_EXECUTOR_ID.equals(executorId)) {
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
          if (app.getState() == FlutterApp.State.STARTING ||
              app.getState() == FlutterApp.State.RELOADING ||
              app.getState() == FlutterApp.State.RESTARTING) {
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
    protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env)
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
            if (!identicalCommands(app.getCommand(), launchState.runConfig.getCommand(env, app.device()))) {
              // To be safe, relaunch as the arguments to launch have changed.
              try {
                // TODO(jacobr): ideally we shouldn't be synchronously waiting for futures like this
                // but I don't see a better option. In practice this seems fine.
                app.shutdownAsync().get();
              }
              catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                FlutterUtils.warn(LOG, e);
              }
              return launchState.launch(env);
            }

            final FlutterLaunchMode launchMode = FlutterLaunchMode.fromEnv(env);
            if (launchMode.supportsReload() && app.isStarted()) {
              // Map a re-run action to a flutter hot restart.
              FileDocumentManager.getInstance().saveAllDocuments();
              FlutterInitializer.sendAnalyticsAction(RestartFlutterApp.class.getSimpleName());
              app.performRestartApp(FlutterConstants.RELOAD_REASON_SAVE);
            }
          }

          return null;
        }
      }

      // Else, launch the app.
      return launchState.launch(env);
    }

    private static boolean identicalCommands(GeneralCommandLine a, GeneralCommandLine b) {
      return a.getParametersList().getList().equals(b.getParametersList().getList());
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

  private static final Logger LOG = Logger.getInstance(LaunchState.class);
}

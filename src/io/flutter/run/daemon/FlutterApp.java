/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonObject;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.ObservatoryConnector;
import io.flutter.logging.FlutterConsoleLogManager;
import io.flutter.logging.FlutterLog;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.FlutterDevice;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.common.RunMode;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.MostlySilentOsProcessHandler;
import io.flutter.utils.ProgressHelper;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import io.flutter.vmService.ServiceExtensions;
import io.flutter.vmService.VMServiceManager;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

// TODO(devoncarew): Move this class up to the io.flutter.run package.

/**
 * A running Flutter app.
 */
public class FlutterApp {
  private static final Logger LOG = Logger.getInstance(FlutterApp.class);
  private static final Key<FlutterApp> FLUTTER_APP_KEY = new Key<>("FLUTTER_APP_KEY");

  private final @NotNull Project myProject;
  private final @Nullable Module myModule;
  private final @NotNull RunMode myMode;
  // TODO(jwren): myDevice is not-null for all run configurations except flutter web configurations
  // See https://github.com/flutter/flutter-intellij/issues/3293.
  private final @Nullable FlutterDevice myDevice;
  private final @NotNull ProcessHandler myProcessHandler;
  private final @NotNull ExecutionEnvironment myExecutionEnvironment;
  private final @NotNull DaemonApi myDaemonApi;
  private final @NotNull GeneralCommandLine myCommand;

  private @Nullable String myAppId;
  private @Nullable String myWsUrl;
  private @Nullable String myBaseUri;
  private @Nullable ConsoleView myConsole;
  private FlutterConsoleLogManager myFlutterConsoleLogManager;

  private boolean isFlutterWeb = false;

  /**
   * The command with which the app was launched.
   * <p>
   * Should be "run" if the app was `flutter run` and "attach" if the app was `flutter attach`.
   */
  private @Nullable String myLaunchMode;

  private @Nullable List<PubRoot> myPubRoots;

  private int reloadCount;
  private int userReloadCount;
  private int restartCount;

  private long maxFileTimestamp;

  /**
   * Non-null when the debugger is paused.
   */
  private @Nullable Runnable myResume;

  private final AtomicReference<State> myState = new AtomicReference<>(State.STARTING);
  private final EventDispatcher<FlutterAppListener> listenersDispatcher = EventDispatcher.create(FlutterAppListener.class);

  private final FlutterLog myFlutterLog;
  private final ObservatoryConnector myConnector;
  private @Nullable FlutterDebugProcess myFlutterDebugProcess;
  private @Nullable VmService myVmService;
  private @Nullable VMServiceManager myVMServiceManager;

  private static final Key<FlutterApp> APP_KEY = Key.create("FlutterApp");

  public static void addToEnvironment(@NotNull ExecutionEnvironment env, @NotNull FlutterApp app) {
    env.putUserData(APP_KEY, app);
  }

  @Nullable
  public static FlutterApp fromEnv(@NotNull ExecutionEnvironment env) {
    return env.getUserData(APP_KEY);
  }

  FlutterApp(@NotNull Project project,
             @Nullable Module module,
             @NotNull RunMode mode,
             @Nullable FlutterDevice device,
             @NotNull ProcessHandler processHandler,
             @NotNull ExecutionEnvironment executionEnvironment,
             @NotNull DaemonApi daemonApi,
             @NotNull GeneralCommandLine command) {
    myProject = project;
    myModule = module;
    myFlutterLog = new FlutterLog(project, module);
    myMode = mode;
    myDevice = device;
    myProcessHandler = processHandler;
    myProcessHandler.putUserData(FLUTTER_APP_KEY, this);
    myExecutionEnvironment = executionEnvironment;
    myDaemonApi = daemonApi;
    myCommand = command;
    maxFileTimestamp = System.currentTimeMillis();
    myConnector = new ObservatoryConnector() {
      @Override
      public @Nullable
      String getWebSocketUrl() {
        // Don't try to use observatory until the flutter command is done starting up.
        if (getState() != State.STARTED) return null;
        return myWsUrl;
      }

      public @Nullable
      String getBrowserUrl() {
        String url = myWsUrl;
        if (url == null) return null;
        if (url.startsWith("ws:")) {
          url = "http:" + url.substring(3);
        }
        if (url.endsWith("/ws")) {
          url = url.substring(0, url.length() - 3);
        }
        return url;
      }

      @Override
      public String getRemoteBaseUrl() {
        return myBaseUri;
      }

      @Override
      public void onDebuggerPaused(@NotNull Runnable resume) {
        myResume = resume;
      }

      @Override
      public void onDebuggerResumed() {
        myResume = null;
      }
    };
  }

  @NotNull
  public FlutterLog getFlutterLog() {
    return myFlutterLog;
  }

  @NotNull
  public GeneralCommandLine getCommand() {
    return myCommand;
  }

  @Nullable
  public static FlutterApp fromProcess(@NotNull ProcessHandler process) {
    return process.getUserData(FLUTTER_APP_KEY);
  }

  @Nullable
  public static FlutterApp firstFromProjectProcess(@NotNull Project project) {
    final List<RunContentDescriptor> runningProcesses =
      ExecutionManager.getInstance(project).getContentManager().getAllDescriptors();
    for (RunContentDescriptor descriptor : runningProcesses) {
      final ProcessHandler process = descriptor.getProcessHandler();
      if (process != null) {
        final FlutterApp app = FlutterApp.fromProcess(process);
        if (app != null) {
          return app;
        }
      }
    }

    return null;
  }

  @NotNull
  public static List<FlutterApp> allFromProjectProcess(@NotNull Project project) {
    final List<FlutterApp> allRunningApps = new ArrayList<>();
    final List<RunContentDescriptor> runningProcesses =
      ExecutionManager.getInstance(project).getContentManager().getAllDescriptors();
    for (RunContentDescriptor descriptor : runningProcesses) {
      final ProcessHandler process = descriptor.getProcessHandler();
      if (process != null) {
        final FlutterApp app = FlutterApp.fromProcess(process);
        if (app != null) {
          allRunningApps.add(app);
        }
      }
    }

    return allRunningApps;
  }

  /**
   * Creates a process that will launch the flutter app.
   * <p>
   * (Assumes we are launching it in --machine mode.)
   */
  @NotNull
  public static FlutterApp start(@NotNull ExecutionEnvironment env,
                                 @NotNull Project project,
                                 @Nullable Module module,
                                 @NotNull RunMode mode,
                                 @Nullable FlutterDevice device,
                                 @NotNull GeneralCommandLine command,
                                 @Nullable String analyticsStart,
                                 @Nullable String analyticsStop)
    throws ExecutionException {
    LOG.info(analyticsStart + " " + project.getName() + " (" + mode.mode() + ")");
    LOG.info(command.toString());

    final ProcessHandler process = new MostlySilentOsProcessHandler(command);
    Disposer.register(project, process::destroyProcess);

    // Send analytics for the start and stop events.
    if (analyticsStart != null) {
      FlutterInitializer.sendAnalyticsAction(analyticsStart);
    }

    final DaemonApi api = new DaemonApi(process);
    final FlutterApp app = new FlutterApp(project, module, mode, device, process, env, api, command);

    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        LOG.info(analyticsStop + " " + project.getName() + " (" + mode.mode() + ")");
        if (analyticsStop != null) {
          FlutterInitializer.sendAnalyticsAction(analyticsStop);
        }

        // Send analytics about whether this session used the reload workflow, the restart workflow, or neither.
        final String workflowType = app.reloadCount > 0 ? "reload" : (app.restartCount > 0 ? "restart" : "none");
        FlutterInitializer.getAnalytics().sendEvent("workflow", workflowType);

        // Send the ratio of reloads to restarts.
        int reloadfraction = 0;
        if ((app.reloadCount + app.restartCount) > 0) {
          final double fraction = (app.reloadCount * 100.0) / (app.reloadCount + app.restartCount);
          reloadfraction = (int)Math.round(fraction);
        }
        FlutterInitializer.getAnalytics().sendEventMetric("workflow", "reloadFraction", reloadfraction);
      }
    });

    api.listen(process, new FlutterAppDaemonEventListener(app, project));

    return app;
  }

  @NotNull
  public RunMode getMode() {
    return myMode;
  }

  /**
   * Returns the process running the daemon.
   */
  @NotNull
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  @NotNull
  public ObservatoryConnector getConnector() {
    return myConnector;
  }

  public void setIsFlutterWeb(boolean value) {
    isFlutterWeb = value;
  }

  public boolean getIsFlutterWeb() {
    return isFlutterWeb;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean appSupportsHotReload() {
    return !isFlutterWeb;
  }

  public State getState() {
    return myState.get();
  }

  public boolean isStarted() {
    final State state = myState.get();
    return state != State.STARTING && state != State.TERMINATED;
  }

  public boolean isReloading() {
    return myState.get() == State.RELOADING || myState.get() == State.RESTARTING;
  }

  public boolean isConnected() {
    return myState.get() != State.TERMINATING && myState.get() != State.TERMINATED;
  }

  void setAppId(@NotNull String id) {
    myAppId = id;
  }

  void setWsUrl(@NotNull String url) {
    myWsUrl = url;
  }

  void setBaseUri(@NotNull String uri) {
    myBaseUri = uri;
  }

  void setLaunchMode(@Nullable String launchMode) {
    myLaunchMode = launchMode;
  }

  /**
   * Perform a hot restart of the the app.
   */
  public CompletableFuture<DaemonApi.RestartResult> performRestartApp(@NotNull String reason) {
    if (myAppId == null) {
      FlutterUtils.warn(LOG, "cannot restart Flutter app because app id is not set");

      final CompletableFuture<DaemonApi.RestartResult> result = new CompletableFuture<>();
      result.completeExceptionally(new IllegalStateException("cannot restart Flutter app because app id is not set"));
      return result;
    }

    restartCount++;
    userReloadCount = 0;

    LocalHistory.getInstance().putSystemLabel(getProject(), "Flutter hot restart");

    maxFileTimestamp = System.currentTimeMillis();
    changeState(State.RESTARTING);

    final CompletableFuture<DaemonApi.RestartResult> future =
      myDaemonApi.restartApp(myAppId, true, false, reason);
    future.thenAccept(result -> changeState(State.STARTED));
    future.thenRun(this::notifyAppRestarted);
    return future;
  }

  private void notifyAppReloaded() {
    listenersDispatcher.getMulticaster().notifyAppReloaded();
  }

  private void notifyAppRestarted() {
    listenersDispatcher.getMulticaster().notifyAppRestarted();
  }

  public boolean isSameModule(@Nullable final Module other) {
    return Objects.equals(myModule, other);
  }

  /**
   * @return whether the latest of the version of the file is running.
   */
  public boolean isLatestVersionRunning(VirtualFile file) {
    return file != null && file.getTimeStamp() <= maxFileTimestamp;
  }

  /**
   * Perform a hot reload of the app.
   */
  public CompletableFuture<DaemonApi.RestartResult> performHotReload(boolean pauseAfterRestart, @NotNull String reason) {
    if (myAppId == null) {
      FlutterUtils.warn(LOG, "cannot reload Flutter app because app id is not set");

      final CompletableFuture<DaemonApi.RestartResult> result = new CompletableFuture<>();
      result.completeExceptionally(new IllegalStateException("cannot reload Flutter app because app id is not set"));
      return result;
    }

    reloadCount++;
    userReloadCount++;

    LocalHistory.getInstance().putSystemLabel(getProject(), "hot reload #" + userReloadCount);

    maxFileTimestamp = System.currentTimeMillis();
    changeState(State.RELOADING);

    final CompletableFuture<DaemonApi.RestartResult> future =
      myDaemonApi.restartApp(myAppId, false, pauseAfterRestart, reason);
    future.thenAccept(result -> changeState(State.STARTED));
    future.thenRun(this::notifyAppReloaded);
    return future;
  }

  public CompletableFuture<String> togglePlatform() {
    if (myAppId == null) {
      FlutterUtils.warn(LOG, "cannot invoke togglePlatform on Flutter app because app id is not set");
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<JsonObject> result = callServiceExtension(ServiceExtensions.togglePlatformMode.getExtension());
    return result.thenApply(obj -> {
      //noinspection CodeBlock2Expr
      return obj.get("value").getAsString();
    });
  }

  public CompletableFuture<String> togglePlatform(String platform) {
    if (myAppId == null) {
      FlutterUtils.warn(LOG, "cannot invoke togglePlatform on Flutter app because app id is not set");
      return CompletableFuture.completedFuture(null);
    }

    final Map<String, Object> params = new HashMap<>();
    params.put("value", platform);
    return callServiceExtension(ServiceExtensions.togglePlatformMode.getExtension(), params)
      .thenApply(obj -> {
        //noinspection CodeBlock2Expr
        return obj != null ? obj.get("value").getAsString() : null;
      });
  }

  public CompletableFuture<JsonObject> callServiceExtension(String methodName) {
    return callServiceExtension(methodName, new HashMap<>());
  }

  public CompletableFuture<JsonObject> callServiceExtension(String methodName, Map<String, Object> params) {
    if (myAppId == null) {
      FlutterUtils.warn(LOG, "cannot invoke " + methodName + " on Flutter app because app id is not set");
      return CompletableFuture.completedFuture(null);
    }
    if (isFlutterIsolateSuspended()) {
      return whenFlutterIsolateResumed().thenComposeAsync((ignored) ->
                                                            myDaemonApi.callAppServiceExtension(myAppId, methodName, params)
      );
    }
    else {
      return myDaemonApi.callAppServiceExtension(myAppId, methodName, params);
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  public CompletableFuture<Boolean> callBooleanExtension(String methodName, boolean enabled) {
    final Map<String, Object> params = new HashMap<>();
    params.put("enabled", enabled);
    return callServiceExtension(methodName, params).thenApply(obj -> {
      //noinspection CodeBlock2Expr
      return obj == null ? null : obj.get("enabled").getAsBoolean();
    });
  }

  /**
   * Call a boolean service extension only if it is already present, skipping
   * otherwise.
   * <p>
   * Only use this method if you are confident there will not be a race
   * condition where the service extension is registered shortly after
   * this method is called.
   */
  @SuppressWarnings("UnusedReturnValue")
  public CompletableFuture<Boolean> maybeCallBooleanExtension(String methodName, boolean enabled) {
    if (getVMServiceManager() != null && getVMServiceManager().hasServiceExtensionNow(methodName)) {
      return callBooleanExtension(methodName, enabled);
    }
    return CompletableFuture.completedFuture(false);
  }

  @Nullable
  public StreamSubscription<Boolean> hasServiceExtension(String name, Consumer<Boolean> onData) {
    if (getVMServiceManager() == null) {
      return null;
    }
    return getVMServiceManager().hasServiceExtension(name, onData);
  }

  public void hasServiceExtension(String name, Consumer<Boolean> onData, Disposable parentDisposable) {
    if (getVMServiceManager() != null) {
      getVMServiceManager().hasServiceExtension(name, onData, parentDisposable);
    }
  }

  public void setConsole(@Nullable ConsoleView console) {
    myConsole = console;
  }

  @Nullable
  public ConsoleView getConsole() {
    return myConsole;
  }

  /**
   * Transitions to a new state and fires events.
   * <p>
   * If no change is needed, returns false and does not fire events.
   */
  boolean changeState(State newState) {
    final State oldState = myState.getAndSet(newState);
    if (oldState == newState) {
      return false; // debounce
    }
    listenersDispatcher.getMulticaster().stateChanged(newState);
    return true;
  }

  /**
   * Starts shutting down the process.
   * <p>
   * If possible, we want to shut down gracefully by sending a stop command to the application.
   */
  public Future shutdownAsync() {
    final FutureTask done = new FutureTask<>(() -> null);
    if (!changeState(State.TERMINATING)) {
      done.run();
      return done; // Debounce; already shutting down.
    }

    if (myResume != null) {
      myResume.run();
    }

    final String appId = myAppId;
    if (appId == null) {
      // If it it didn't finish starting up, shut down abruptly.
      myProcessHandler.destroyProcess();
      done.run();
      return done;
    }

    // Do the rest in the background to avoid freezing the Swing dispatch thread.
    AppExecutorUtil.getAppExecutorService().submit(() -> {
      // Try to shut down gracefully (need to wait for a response).
      final Future stopDone;
      if (DaemonEvent.AppStarting.LAUNCH_MODE_ATTACH.equals(myLaunchMode)) {
        stopDone = myDaemonApi.detachApp(appId);
      }
      else {
        stopDone = myDaemonApi.stopApp(appId);
      }
      final Stopwatch watch = Stopwatch.createStarted();
      while (watch.elapsed(TimeUnit.SECONDS) < 10 && getState() == State.TERMINATING) {
        try {
          stopDone.get(100, TimeUnit.MILLISECONDS);
          break;
        }
        catch (TimeoutException e) {
          // continue
        }
        catch (Exception e) {
          FlutterUtils.warn(LOG, e);
          break;
        }
      }

      // If it didn't work, shut down abruptly.
      myProcessHandler.destroyProcess();
      myDaemonApi.cancelPending();
      done.run();
    });
    return done;
  }

  public void addStateListener(@NotNull FlutterAppListener listener) {
    listenersDispatcher.addListener(listener);
    listener.stateChanged(myState.get());
  }

  public void removeStateListener(@NotNull FlutterAppListener listener) {
    listenersDispatcher.removeListener(listener);
  }

  public FlutterLaunchMode getLaunchMode() {
    return FlutterLaunchMode.fromEnv(myExecutionEnvironment);
  }

  public boolean isSessionActive() {
    final FlutterDebugProcess debugProcess = getFlutterDebugProcess();
    return isStarted() && debugProcess != null && debugProcess.getVmConnected() &&
           !debugProcess.getSession().isStopped();
  }

  @Nullable
  public FlutterDevice device() {
    return myDevice;
  }

  @Nullable
  public String deviceId() {
    return myDevice != null ? myDevice.deviceId() : null;
  }

  public void setFlutterDebugProcess(FlutterDebugProcess flutterDebugProcess) {
    myFlutterDebugProcess = flutterDebugProcess;
    myFlutterLog.setFlutterApp(this);
  }

  public FlutterDebugProcess getFlutterDebugProcess() {
    return myFlutterDebugProcess;
  }

  public void setVmServices(@NotNull VmService vmService, VMServiceManager vmServiceManager) {
    myVmService = vmService;
    myVMServiceManager = vmServiceManager;

    myVmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {
          if (StringUtil.equals("Flutter.Frame", event.getExtensionKind())) {
            listenersDispatcher.getMulticaster().notifyFrameRendered();
          }
        }
      }
    });

    listenersDispatcher.getMulticaster().notifyVmServiceAvailable(vmService);

    // Init the app's FlutterConsoleLogManager.
    getFlutterConsoleLogManager();
  }

  @Nullable
  public VmService getVmService() {
    return myVmService;
  }

  @Nullable
  public VMServiceManager getVMServiceManager() {
    return myVMServiceManager;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<PubRoot> getPubRoots() {
    if (myPubRoots == null) {
      myPubRoots = PubRoots.forProject(myProject);
    }
    return myPubRoots;
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  public FlutterConsoleLogManager getFlutterConsoleLogManager() {
    if (myFlutterConsoleLogManager == null) {
      assert (getConsole() != null);
      myFlutterConsoleLogManager = new FlutterConsoleLogManager(getConsole(), this);

      if (FlutterSettings.getInstance().isShowStructuredErrors()) {
        // Calling this will override the default Flutter stdout error display.
        hasServiceExtension(ServiceExtensions.toggleShowStructuredErrors.getExtension(), (present) -> {
          if (present) {
            callBooleanExtension(ServiceExtensions.toggleShowStructuredErrors.getExtension(), true);
          }
        });
      }
    }

    return myFlutterConsoleLogManager;
  }

  @Override
  public String toString() {
    return myExecutionEnvironment.toString() + ":" + deviceId();
  }

  public boolean isFlutterIsolateSuspended() {
    if (!isSessionActive() || myVMServiceManager.getCurrentFlutterIsolateRaw() == null) {
      // The isolate cannot be suspended if it isn't running yet.
      return false;
    }
    return getFlutterDebugProcess().isIsolateSuspended(myVMServiceManager.getCurrentFlutterIsolateRaw().getId());
  }

  private CompletableFuture<?> whenFlutterIsolateResumed() {
    if (!isFlutterIsolateSuspended()) {
      return CompletableFuture.completedFuture(null);
    }
    return getFlutterDebugProcess().whenIsolateResumed(myVMServiceManager.getCurrentFlutterIsolateRaw().getId());
  }

  public interface FlutterAppListener extends EventListener {
    default void stateChanged(State newState) {
    }

    default void notifyAppReloaded() {
    }

    default void notifyAppRestarted() {
    }

    default void notifyFrameRendered() {
    }

    default void notifyVmServiceAvailable(VmService vmService) {
    }
  }

  public enum State {STARTING, STARTED, RELOADING, RESTARTING, TERMINATING, TERMINATED}
}

/**
 * Listens for events while running or debugging an app.
 */
class FlutterAppDaemonEventListener implements DaemonEvent.Listener {
  private static final Logger LOG = Logger.getInstance(FlutterAppDaemonEventListener.class);

  private final @NotNull FlutterApp app;
  private final @NotNull ProgressHelper progress;

  private final AtomicReference<Stopwatch> stopwatch = new AtomicReference<>();

  FlutterAppDaemonEventListener(@NotNull FlutterApp app, @NotNull Project project) {
    this.app = app;
    this.progress = new ProgressHelper(project);
  }

  // process lifecycle

  @Override
  public void processWillTerminate() {
    progress.cancel();
    // Shutdown must be sync so that we prevent the processTerminated() event from being delivered
    // until a graceful shutdown has been tried.
    try {
      app.shutdownAsync().get(100, TimeUnit.MILLISECONDS);
    }
    catch (TimeoutException e) {
      LOG.info("app shutdown took longer than 100ms");
    }
    catch (Exception e) {
      FlutterUtils.warn(LOG, "exception while shutting down Flutter App", e);
    }
  }

  @Override
  public void processTerminated(int exitCode) {
    progress.cancel();
    app.changeState(FlutterApp.State.TERMINATED);
  }

  // daemon domain

  @Override
  public void onDaemonLog(@NotNull DaemonEvent.DaemonLog message) {
    final ConsoleView console = app.getConsole();
    if (console == null) return;
    if (message.log != null) {
      console.print(message.log + "\n", message.error ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  @Override
  public void onDaemonLogMessage(@NotNull DaemonEvent.DaemonLogMessage message) {
    LOG.info("flutter app: " + message.message);
  }

  // app domain

  @Override
  public void onAppStarting(DaemonEvent.AppStarting event) {
    app.setAppId(event.appId);
    app.setLaunchMode(event.launchMode);
  }

  @Override
  public void onAppDebugPort(@NotNull DaemonEvent.AppDebugPort port) {
    app.setWsUrl(port.wsUri);

    String uri = port.baseUri;
    if (uri == null) return;

    if (uri.startsWith("file:")) {
      // Convert the file: url to a path.
      try {
        uri = new URL(uri).getPath();
        if (uri.endsWith(File.separator)) {
          uri = uri.substring(0, uri.length() - 1);
        }
      }
      catch (MalformedURLException e) {
        // ignore
      }
    }
    app.setBaseUri(uri);
  }

  @Override
  public void onAppStarted(DaemonEvent.AppStarted started) {
    app.changeState(FlutterApp.State.STARTED);
  }

  @Override
  public void onAppLog(@NotNull DaemonEvent.AppLog message) {
    final ConsoleView console = app.getConsole();
    if (console == null) return;
    console.print(message.log + "\n", message.error ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT);
  }

  @Override
  public void onAppProgressStarting(@NotNull DaemonEvent.AppProgress event) {
    progress.start(event.message);

    if (event.getType().startsWith("hot.")) {
      // We clear the console view in order to help indicate that a reload is happening.
      if (app.getConsole() != null) {
        if (!FlutterSettings.getInstance().isVerboseLogging()) {
          app.getConsole().clear();
        }
      }

      stopwatch.set(Stopwatch.createStarted());
    }

    if (app.getConsole() != null) {
      app.getConsole().print(event.message + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  @Override
  public void onAppProgressFinished(@NotNull DaemonEvent.AppProgress event) {
    progress.done();
    final Stopwatch watch = stopwatch.getAndSet(null);
    if (watch != null) {
      watch.stop();
      switch (event.getType()) {
        case "hot.reload":
          reportElapsed(watch, "Reloaded", "reload");
          break;
        case "hot.restart":
          reportElapsed(watch, "Restarted", "restart");
          break;
      }
    }
  }

  private void reportElapsed(@NotNull Stopwatch watch, String verb, String analyticsName) {
    final long elapsedMs = watch.elapsed(TimeUnit.MILLISECONDS);
    FlutterInitializer.getAnalytics().sendTiming("run", analyticsName, elapsedMs);
  }

  @Override
  public void onAppStopped(@NotNull DaemonEvent.AppStopped stopped) {
    if (stopped.error != null && app.getConsole() != null) {
      app.getConsole().print("Finished with error: " + stopped.error + "\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
    progress.cancel();
    app.getProcessHandler().destroyProcess();
  }
}

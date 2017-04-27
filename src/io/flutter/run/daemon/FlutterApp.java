/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonObject;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A running Flutter app.
 */
public class FlutterApp {

  private static final Key<FlutterApp> FLUTTER_APP_KEY = new Key<>("FLUTTER_APP_KEY");

  private final @NotNull RunMode myMode;
  private final @NotNull ProcessHandler myProcessHandler;
  private final @NotNull DaemonApi myDaemonApi;

  private @Nullable String myAppId;
  private @Nullable String myWsUrl;
  private @Nullable String myBaseUri;
  private @Nullable ConsoleView myConsole;

  /**
   * Non-null when the debugger is paused.
   */
  private @Nullable Runnable myResume;

  private final AtomicReference<State> myState = new AtomicReference<>(State.STARTING);
  private final List<StateListener> myListeners = new ArrayList<>();

  private final ObservatoryConnector myConnector;

  FlutterApp(@NotNull RunMode mode,
             @NotNull ProcessHandler processHandler,
             @NotNull DaemonApi daemonApi) {
    myMode = mode;
    myProcessHandler = processHandler;
    myProcessHandler.putUserData(FLUTTER_APP_KEY, this);
    myDaemonApi = daemonApi;
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

  @Nullable
  public static FlutterApp fromProcess(@NotNull ProcessHandler process) {
    return process.getUserData(FLUTTER_APP_KEY);
  }

  /**
   * Creates a process that will launch the flutter app.
   * <p>
   * (Assumes we are launching it in --machine mode.)
   */
  @NotNull
  public static FlutterApp start(Project project, @NotNull RunMode mode,
                                 @NotNull GeneralCommandLine command,
                                 @NotNull String analyticsStart,
                                 @NotNull String analyticsStop)
    throws ExecutionException {

    final ProcessHandler process = new OSProcessHandler(command);
    Disposer.register(project, process::destroyProcess);

    // Send analytics for the start and stop events.
    FlutterInitializer.sendAnalyticsAction(analyticsStart);
    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        FlutterInitializer.sendAnalyticsAction(analyticsStop);
      }
    });

    final DaemonApi api = new DaemonApi(process);
    final FlutterApp app = new FlutterApp(mode, process, api);
    api.listen(process, new FlutterAppListener(app, project));
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

  public State getState() {
    return myState.get();
  }

  public boolean isStarted() {
    return myState.get() == State.STARTED;
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

  /**
   * Perform a full restart of the the app.
   */
  public void performRestartApp() {
    if (myAppId == null) {
      LOG.warn("cannot restart Flutter app because app id is not set");
      return;
    }
    myDaemonApi.restartApp(myAppId, true, false)
      .thenRunAsync(() -> changeState(FlutterApp.State.STARTED));
  }

  /**
   * Perform a hot reload of the app.
   */
  public void performHotReload(boolean pauseAfterRestart) {
    if (myAppId == null) {
      LOG.warn("cannot reload Flutter app because app id is not set");
      return;
    }
    myDaemonApi.restartApp(myAppId, false, pauseAfterRestart)
      .thenRunAsync(() -> changeState(FlutterApp.State.STARTED));
  }

  public CompletableFuture<Boolean> togglePlatform() {
    if (myAppId == null) {
      LOG.warn("cannot invoke togglePlatform on Flutter app because app id is not set");
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<JsonObject> result = callServiceExtension("ext.flutter.platformOverride");
    return result.thenApply(obj -> {
      //noinspection CodeBlock2Expr
      return obj != null && "android".equals(obj.get("value").getAsString());
    });
  }

  public CompletableFuture<Boolean> togglePlatform(boolean showAndroid) {
    if (myAppId == null) {
      LOG.warn("cannot invoke togglePlatform on Flutter app because app id is not set");
      return CompletableFuture.completedFuture(null);
    }

    final Map<String, Object> params = new HashMap<>();
    params.put("value", showAndroid ? "android" : "iOS");
    return callServiceExtension("ext.flutter.platformOverride", params).thenApply(obj -> {
      //noinspection CodeBlock2Expr
      return obj != null && "android".equals(obj.get("value").getAsString());
    });
  }

  public CompletableFuture<JsonObject> callServiceExtension(String methodName) {
    return callServiceExtension(methodName, new HashMap<>());
  }

  public CompletableFuture<JsonObject> callServiceExtension(String methodName, Map<String, Object> params) {
    if (myAppId == null) {
      LOG.warn("cannot invoke " + methodName + " on Flutter app because app id is not set");
      return CompletableFuture.completedFuture(null);
    }
    return myDaemonApi.callAppServiceExtension(myAppId, methodName, params);
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
    myListeners.iterator().forEachRemaining(x -> x.stateChanged(newState));
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
      // Try to shut down gracefully. (Need to wait for a response.)
      final Future stopDone = myDaemonApi.stopApp(appId);
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
          LOG.warn(e);
          break;
        }
      }

      // If it didn't work, shut down abruptly.
      myProcessHandler.destroyProcess();
      done.run();
    });
    return done;
  }

  public void addStateListener(@NotNull StateListener listener) {
    myListeners.add(listener);
    listener.stateChanged(myState.get());
  }

  public void removeStateListener(@NotNull StateListener listener) {
    myListeners.remove(listener);
  }

  public interface StateListener {
    void stateChanged(State newState);
  }

  public enum State {STARTING, STARTED, TERMINATING, TERMINATED}

  private static final Logger LOG = Logger.getInstance(FlutterApp.class);
}

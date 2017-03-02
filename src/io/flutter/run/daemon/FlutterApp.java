/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A running Flutter app.
 */
public class FlutterApp {
  private final @NotNull ProcessHandler myProcessHandler;
  private final @NotNull DaemonApi myDaemonApi;

  private @Nullable String myAppId;
  private @Nullable String myWsUrl;
  private @Nullable String myBaseUri;
  private @Nullable ConsoleView myConsole;

  /**
   * Non-null when the debugger is paused.
   */
  private @Nullable XDebugSession mySessionHook;

  private final AtomicReference<State> myState = new AtomicReference<>(State.STARTING);
  private final List<StateListener> myListeners = new ArrayList<>();

  public FlutterApp(@NotNull ProcessHandler processHandler,
                    @NotNull DaemonApi daemonApi) {
    myProcessHandler = processHandler;
    myDaemonApi = daemonApi;
  }

  /**
   * Returns the process running the daemon.
   */
  public @NotNull ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  void setAppId(@NotNull String id) {
    myAppId = id;
  }

  /**
   * @return the Observatory WebSocket URL
   */
  @Nullable
  public String wsUrl() {
    return myWsUrl;
  }

  void setWsUrl(@NotNull String url) {
    myWsUrl = url;
  }

  /**
   * @return The (optional) baseUri to use for debugger paths.
   */
  @Nullable
  public String baseUri() {
    return myBaseUri;
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

  public void callServiceExtension(String methodName, Map<String, Object> params) {
    if (myAppId == null) {
      LOG.warn("cannot invoke " + methodName + " on Flutter app because app id is not set");
      return;
    }
    myDaemonApi.callAppServiceExtension(myAppId, methodName, params);
  }

  public void setConsole(@Nullable ConsoleView console) {
    myConsole = console;
  }

  public @Nullable ConsoleView getConsole() {
    return myConsole;
  }

  public void onPause(XDebugSession sessionHook) {
    mySessionHook = sessionHook;
  }

  public void onResume() {
    mySessionHook = null;
  }

  /**
   * Transitions to a new state and fires events.
   *
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
   *
   * <p>If possible, we want to shut down gracefully by sending a stop command to the application.
   */
  Future shutdownAsync() {
    final FutureTask done = new FutureTask<>(() -> null);
    if (!changeState(State.TERMINATING)) {
      done.run();
      return done; // Debounce; already shutting down.
    }

    // Resume if paused.
    if (mySessionHook != null && mySessionHook.isPaused()) {
      mySessionHook.resume();
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
      try {
        stopDone.get(10, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        // Probably timed out.
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

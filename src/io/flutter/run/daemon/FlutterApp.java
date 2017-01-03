/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A running Flutter app.
 */
public class FlutterApp {
  private final FlutterDaemonController myController;
  private final FlutterDaemonControllerHelper myManager;
  private String myAppId;
  private final RunMode myMode;
  private final Project myProject;
  private final boolean isHot;
  private String myWsUrl;
  private String myBaseUri;
  private ConsoleView myConsole;
  private XDebugSession mySesionHook;
  private State myState;
  private final List<StateListener> myListeners = new ArrayList<>();

  public FlutterApp(@NotNull FlutterDaemonController controller,
                    @NotNull FlutterDaemonControllerHelper manager,
                    @NotNull RunMode mode,
                    @NotNull Project project,
                    boolean hot) {
    myController = controller;
    myManager = manager;
    myMode = mode;
    myProject = project;
    isHot = hot;
  }

  /**
   * @return The FlutterDaemonController that controls the process running the daemon.
   */
  public FlutterDaemonController getController() {
    return myController;
  }

  /**
   * @return <code>true</code> if the appId has been set. This is set asynchronously after the app is launched.
   */
  public boolean hasAppId() {
    return myAppId != null;
  }

  /**
   * @return The appId for the running app.
   */
  public String appId() {
    return myAppId;
  }

  public void setAppId(String id) {
    myAppId = id;
  }

  /**
   * @return <code>true</code> if the app is in hot-restart mode.
   */
  public boolean isHot() {
    return isHot;
  }

  /**
   * @return The mode the app is running in.
   */
  public RunMode mode() {
    return myMode;
  }

  /**
   * @return The project associated with this app.
   */
  public Project project() {
    return myProject;
  }

  /**
   * @return the Observatory WebSocket URL
   */
  @Nullable
  public String wsUrl() {
    return myWsUrl;
  }

  public void setWsUrl(@NotNull String url) {
    myWsUrl = url;
  }

  /**
   * @return The (optional) baseUri to use for debugger paths.
   */
  @Nullable
  public String baseUri() {
    return myBaseUri;
  }

  public void setBaseUri(String uri) {
    myBaseUri = uri;
  }

  /**
   * Perform a full restart of the the app.
   */
  public void performRestartApp() {
    myManager.restartApp(this, true, false);
  }

  /**
   * Perform a hot reload of the app.
   */
  public void performHotReload(boolean pauseAfterRestart) {
    myManager.restartApp(this, false, pauseAfterRestart);
  }

  /**
   * Fetch the widget hierarchy.
   *
   * @return Unknown
   */
  public Object fetchWidgetHierarchy() {
    throw new NoSuchMethodError("fetchWidgetHierarchy");
  }

  /**
   * Fetch the render tree
   *
   * @return Unknown
   */
  public Object fetchRenderTree() {
    throw new NoSuchMethodError("fetchRenderTree");
  }

  public void setConsole(ConsoleView console) {
    myConsole = console;
  }

  public ConsoleView getConsole() {
    return myConsole;
  }

  public boolean isSessionPaused() {
    return mySesionHook != null;
  }

  public void sessionPaused(XDebugSession sessionHook) {
    mySesionHook = sessionHook;
  }

  public void sessionResumed() {
    mySesionHook = null;
  }

  public void forceResume() {
    if (mySesionHook != null && mySesionHook.isPaused()) {
      mySesionHook.resume();
    }
  }

  public void changeState(State newState) {
    myState = newState;
    myListeners.iterator().forEachRemaining(x -> x.stateChanged(myState));
  }

  public void addStateListener(StateListener listener) {
    myListeners.add(listener);
    listener.stateChanged(myState);
  }

  public void removeStateListener(StateListener listener) {
    myListeners.remove(null);
  }


  public interface StateListener {
    void stateChanged(State newState);
  }

  public enum State {STARTING, STARTED, TERMINATING, TERMINATED}
}

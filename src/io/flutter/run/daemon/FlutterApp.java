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

import java.util.ArrayList;
import java.util.List;

/**
 * Handle for a running Flutter app.
 */
public interface FlutterApp {

  /**
   * @return The FlutterDaemonService used to communicate with the Flutter app.
   */
  FlutterDaemonService getService();

  /**
   * @return The FlutterDaemonController that controls the process running the daemon.
   */
  FlutterDaemonController getController();

  /**
   * @return <code>true</code> if the appId has been set. This is set asynchronously after the app is launched.
   */
  boolean hasAppId();

  /**
   * @return The appId for the running app.
   */
  String appId();

  /**
   * @return <code>true</code> if the app is in hot-restart mode.
   */
  boolean isHot();

  /**
   * @return The mode the app is running in.
   */
  RunMode mode();

  /**
   * @return The project associated with this app.
   */
  Project project();

  /**
   * @return The debug port used to talk to the observatory.
   */
  int port();

  /**
   * @return The (optional) baseUri to use for debugger paths.
   */
  String baseUri();

  /**
   * Stop the app.
   */
  void performStop();

  /**
   * Perform a full restart of the the app.
   */
  void performRestartApp();

  /**
   * Perform a hot reload of the app.
   */
  void performHotReload(boolean pauseAfterRestart);

  /**
   * Fetch the widget hierarchy.
   *
   * @return Unknown
   */
  Object fetchWidgetHierarchy();

  /**
   * Fetch the render tree
   *
   * @return Unknown
   */
  Object fetchRenderTree();

  void setConsole(ConsoleView console);

  ConsoleView getConsole();

  boolean isSessionPaused();

  void sessionPaused(XDebugSession sessionHook);

  void sessionResumed();

  void forceResume();

  void changeState(State newState);

  void addStateListener(StateListener listener);

  void removeStateListener(StateListener listener);

  interface StateListener {
    void stateChanged(State newState);
  }

  enum State {STARTING, STARTED, TERMINATING, TERMINATED}
}

class RunningFlutterApp implements FlutterApp {

  private final FlutterDaemonService myService;
  private final FlutterDaemonController myController;
  private final FlutterAppManager myManager;
  private String myAppId;
  private final RunMode myMode;
  private final Project myProject;
  private final boolean isHot;
  private int myPort;
  private String myBaseUri;
  private ConsoleView myConsole;
  private XDebugSession mySesionHook;
  private State myState;
  private final List<StateListener> myListeners = new ArrayList<>();

  public RunningFlutterApp(@NotNull FlutterDaemonService service,
                           @NotNull FlutterDaemonController controller,
                           @NotNull FlutterAppManager manager,
                           @NotNull RunMode mode,
                           @NotNull Project project,
                           boolean hot) {
    myService = service;
    myController = controller;
    myManager = manager;
    myMode = mode;
    myProject = project;
    isHot = hot;
  }

  @Override
  public void changeState(State newState) {
    myState = newState;
    myListeners.iterator().forEachRemaining(x -> x.stateChanged(myState));
  }

  @Override
  public void addStateListener(StateListener listener) {
    myListeners.add(listener);
    listener.stateChanged(myState);
  }

  @Override
  public void removeStateListener(StateListener listener) {
    myListeners.remove(null);
  }

  void setAppId(String id) {
    myAppId = id;
  }

  @Override
  public FlutterDaemonService getService() {
    return myService;
  }

  @Override
  public FlutterDaemonController getController() {
    return myController;
  }

  @Override
  public boolean hasAppId() {
    return myAppId != null;
  }

  @Override
  public String appId() {
    return myAppId;
  }

  @Override
  public boolean isHot() {
    return isHot;
  }

  @Override
  public RunMode mode() {
    return myMode;
  }

  @Override
  public Project project() {
    return myProject;
  }

  @Override
  public int port() {
    return myPort;
  }

  void setPort(int port) {
    myPort = port;
  }

  @Override
  public String baseUri() {
    return myBaseUri;
  }

  public void setBaseUri(String baseUri) {
    myBaseUri = baseUri;
  }

  @Override
  public void performStop() {
    myManager.stopApp(this);
  }

  @Override
  public void performRestartApp() {
    myManager.restartApp(this, true, false);
  }

  @Override
  public void performHotReload(boolean pauseAfterRestart) {
    myManager.restartApp(this, false, pauseAfterRestart);
  }

  @Override
  public Object fetchWidgetHierarchy() {
    throw new NoSuchMethodError("fetchWidgetHierarchy");
  }

  @Override
  public Object fetchRenderTree() {
    throw new NoSuchMethodError("fetchRenderTree");
  }

  @Override
  public void setConsole(ConsoleView console) {
    myConsole = console;
  }

  @Override
  public ConsoleView getConsole() {
    return myConsole;
  }

  @Override
  public boolean isSessionPaused() {
    return mySesionHook != null;
  }

  @Override
  public void sessionPaused(XDebugSession sessionHook) {
    mySesionHook = sessionHook;
  }

  @Override
  public void sessionResumed() {
    mySesionHook = null;
  }

  @Override
  public void forceResume() {
    if (mySesionHook != null && mySesionHook.isPaused()) {
      mySesionHook.resume();
    }
  }
}

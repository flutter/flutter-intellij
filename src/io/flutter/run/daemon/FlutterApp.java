/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import static io.flutter.run.daemon.FlutterAppManager.AppStarted;

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
   * @return The Flutter project directory.
   */
  String projectDirectory();

  /**
   * @return The deviceId the app is running on.
   */
  String deviceId();

  /**
   * @return <code>true</code> if the app can be restarted.
   */
  boolean isRestartable();

  /**
   * @return <code>true</code> if the app is in hot-restart mode.
   */
  boolean isHot();

  /**
   * @return The mode the app is running in.
   */
  RunMode mode();

  /**
   * @return The route parameter.
   */
  String route();

  /**
   * @return The target parameter.
   */
  String target();

  /**
   * @return The debug port used to talk to the observatory.
   */
  int port();

  /**
   * Stop the app.
   */
  void performStop();

  /**
   * Restart the app.
   */
  void performRestart();

  /**
   * Refresh the app.
   */
  void performReload();
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
}

class RunningFlutterApp implements FlutterApp {

  private FlutterDaemonService myService;
  private FlutterDaemonController myController;
  private FlutterAppManager myManager;
  private AppStarted myApp;
  private RunMode myMode;
  private boolean isHot;
  private String myRoute;
  private String myTarget;
  private int myPort;

  public RunningFlutterApp(@NotNull FlutterDaemonService service,
                           @NotNull FlutterDaemonController controller,
                           @NotNull FlutterAppManager manager,
                           @NotNull RunMode mode,
                           boolean hot,
                           @Nullable String target,
                           @Nullable String route) {
    myService = service;
    myController = controller;
    myManager = manager;
    myMode = mode;
    isHot = hot;
    myRoute = route;
    myTarget = target;
  }

  void setApp(AppStarted app) {
    myApp = app;
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
    return myApp != null && myApp.appId != null;
  }

  @Override
  public String appId() {
    return myApp.appId;
  }

  @Override
  public String projectDirectory() {
    return myApp.directory;
  }

  @Override
  public String deviceId() {
    return myApp.deviceId;
  }

  @Override
  public boolean isRestartable() {
    return myApp.supportsRestart;
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
  public String route() {
    return myRoute;
  }

  @Override
  public String target() {
    return myTarget;
  }

  @Override
  public int port() {
    return myPort;
  }

  void setPort(int port) {
    myPort = port;
  }

  @Override
  public void performStop() {
    myManager.stopApp(this);
  }

  @Override
  public void performRestart() {
    myManager.restartApp(this, true);
  }

  @Override
  public void performReload() {
    myManager.restartApp(this, true);
  }

  @Override
  public Object fetchWidgetHierarchy() {
    throw new NotImplementedException();
  }

  @Override
  public Object fetchRenderTree() {
    throw new NotImplementedException();
  }
}
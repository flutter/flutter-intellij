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

  FlutterDaemonService getService();

  FlutterDaemonController getController();

  String appId();

  String projectDirectory();

  String deviceId();

  boolean isRestartable();

  boolean isHot();

  RunMode mode();

  String route();

  String target();

  void performStop();

  void performRestart();

  void performRefresh();

  Object fetchWidgetHierarchy();

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
  public void performStop() {
    myManager.stopApp(this);
  }

  @Override
  public void performRestart() {
    myManager.restartApp(this, false);
  }

  void fullRestartApp() {
    myManager.restartApp(this, true);
  }

  @Override
  public void performRefresh() {
    throw new NotImplementedException();
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
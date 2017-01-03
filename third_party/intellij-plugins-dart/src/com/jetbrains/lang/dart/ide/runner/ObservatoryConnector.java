package com.jetbrains.lang.dart.ide.runner;

import com.intellij.xdebugger.XDebugSession;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.Nullable;

public interface ObservatoryConnector {

  /**
   * Return true if the observatory is ready to make a connection.
   */
  boolean isConnectionReady();

  /**
   * Return the WebSocket URL used by the observatory.
   */
  @Nullable
  String getObservatoryWsUrl();

  /**
   * Return the FlutterApp used to control the running app.
   */
  FlutterApp getApp();

  /**
   * The debug session has been paused.
   */
  void sessionPaused(XDebugSession sessionHook);

  /**
   * The debug session has been resumed.
   */
  void sessionResumed();
}

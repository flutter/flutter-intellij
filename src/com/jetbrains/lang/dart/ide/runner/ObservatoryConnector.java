package com.jetbrains.lang.dart.ide.runner;

public interface ObservatoryConnector {

  /**
   * Return true if the observatory is ready to make a connection.
   */
  boolean isConnectionReady();

  /**
   * Return the port used by the observatory.
   */
  int getPort();
}

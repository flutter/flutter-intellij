package io.flutter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides connection settings to an observatory-based debugger, plus a couple of callbacks.
 */
public interface ObservatoryConnector {

  /**
   * Returns the WebSocket URL used by the observatory, or null if the app didn't connect yet.
   */
  @Nullable
  String getWebSocketUrl();

  /**
   * Returns the http URL to open a browser session, if available.
   */
  @Nullable
  String getBrowserUrl();

  @Nullable
  String getRemoteBaseUrl();

  /**
   * Called when the debugger has paused.
   *
   * <p>The callback can be used to tell the debugger to resume executing the program.
   */
  void onDebuggerPaused(@NotNull Runnable resume);

  /**
   * Called when the debugger has resumed execution.
   */
  void onDebuggerResumed();
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>A message received from a Flutter process that's not in response to a particular request.
 *
 * <p>The protocol is specified in
 * <a href="https://github.com/flutter/flutter/wiki/The-flutter-daemon-mode"
 * >The Flutter Daemon Mode</a>.
 */
abstract class DaemonEvent {

  /**
   * Parses an event and sends it to the listener.
   */
  static void dispatch(@NotNull JsonObject obj, @NotNull Listener listener) {
    final JsonPrimitive primEvent = obj.getAsJsonPrimitive("event");
    if (primEvent == null) {
      LOG.error("Missing event field in JSON from flutter process: " + obj);
      return;
    }

    final String eventName = primEvent.getAsString();
    if (eventName == null) {
      LOG.error("Unexpected event field in JSON from flutter process: " + obj);
      return;
    }

    final JsonObject params = obj.getAsJsonObject("params");
    if (params == null) {
      LOG.error("Missing parameters in event from flutter process: " + obj);
      return;
    }

    final DaemonEvent event = create(eventName, params);
    if (event == null) {
      return; // Drop unknown event.
    }

    event.accept(listener);
  }

  private static @Nullable DaemonEvent create(@NotNull String eventName, @NotNull JsonObject params) {
    try {
      switch (eventName) {
        case "daemon.logMessage":
          return GSON.fromJson(params, LogMessage.class);
        case "app.start":
          return GSON.fromJson(params, AppStarting.class);
        case "app.debugPort":
          return GSON.fromJson(params, AppDebugPort.class);
        case "app.started":
          return GSON.fromJson(params, AppStarted.class);
        case "app.log":
          return GSON.fromJson(params, AppLog.class);
        case "app.progress":
          return GSON.fromJson(params, AppProgress.class);
        case "app.stop":
          return GSON.fromJson(params, AppStopped.class);
        case "device.added":
          return GSON.fromJson(params, DeviceAdded.class);
        case "device.removed":
          return GSON.fromJson(params, DeviceRemoved.class);
        default:
          return null; // Drop an unknown event.
      }
    } catch (JsonSyntaxException e) {
      LOG.error("Unexpected parameters in event from flutter process: " + params);
      return null;
    }
  }

  abstract void accept(Listener listener);

  @Override
  public String toString() {
    return GSON.toJson(this, getClass());
  }

  /**
   * Receives events from a Flutter daemon process.
   */
  interface Listener {

    // process lifecycle

    default void processWillTerminate() {}

    default void processTerminated() {}

    // daemon domain

    default void onDaemonLogMessage(LogMessage event) {}

    // app domain

    default void onAppStarting(AppStarting event) {}

    default void onAppDebugPort(AppDebugPort event) {}

    default void onAppStarted(AppStarted event) {}

    default void onAppLog(AppLog event) {}

    default void onAppProgressStarting(AppProgress event) {}

    default void onAppProgressFinished(AppProgress event) {}

    default void onAppStopped(AppStopped event) {}

    // device domain

    default void onDeviceAdded(DeviceAdded event) {}

    default void onDeviceRemoved(DeviceRemoved event) {}
  }

  // daemon domain

  @SuppressWarnings("unused")
  static class LogMessage extends DaemonEvent {
    // "event":"daemon.eventLogMessage"
    String level;
    String message;
    String stackTrace;

    void accept(Listener listener) {
      listener.onDaemonLogMessage(this);
    }
  }

  // app domain

  @SuppressWarnings("unused")
  static class AppStarting extends DaemonEvent {
    // "event":"app.start"
    String appId;
    String deviceId;
    String directory;
    boolean supportsRestart;

    void accept(Listener listener) {
      listener.onAppStarting(this);
    }
  }

  @SuppressWarnings("unused")
  static class AppDebugPort extends DaemonEvent {
    // "event":"app.eventDebugPort"
    String appId;
    // port seems to be deprecated
    // int port;
    String wsUri;
    String baseUri;

    void accept(Listener listener) {
      listener.onAppDebugPort(this);
    }
  }

  @SuppressWarnings("unused")
  static class AppStarted extends DaemonEvent {
    // "event":"app.started"
    String appId;

    void accept(Listener listener) {
      listener.onAppStarted(this);
    }
  }

  @SuppressWarnings("unused")
  static class AppLog extends DaemonEvent {
    // "event":"app.log"
    String appId;
    String log;
    boolean error;

    void accept(Listener listener) {
      listener.onAppLog(this);
    }
  }

  @SuppressWarnings("unused")
  static class AppProgress extends DaemonEvent {
    // "event":"app.progress"

    // (technically undocumented)
    String appId;
    String id;

    /**
     * Undocumented, optional field; seems to be a progress event subtype.
     * See <a href="https://github.com/flutter/flutter/search?q=startProgress+progressId">code</a>.
     */
    private String progressId;

    String message;

    private Boolean finished;

    @NotNull String getType() {
      return progressId == null ? "" : progressId;
    }

    boolean isStarting() {
      return !isFinished();
    }

    boolean isFinished() {
      return finished != null && finished;
    }

    void accept(Listener listener) {
      if (isStarting()) {
        listener.onAppProgressStarting(this);
      } else {
        listener.onAppProgressFinished(this);
      }
    }
  }

  @SuppressWarnings("unused")
  static class AppStopped extends DaemonEvent {
    // "event":"app.stop"
    String appId;

    void accept(Listener listener) {
      listener.onAppStopped(this);
    }
  }

  // device domain

  static class DeviceAdded extends DaemonEvent {
    // "event":"device.added"
    String id;
    String name;
    String platform;
    boolean emulator;

    void accept(Listener listener) {
      listener.onDeviceAdded(this);
    }
  }

  @SuppressWarnings("unused")
  static class DeviceRemoved extends DaemonEvent {
    // "event":"device.removed"
    String id;
    String name;
    String platform;
    boolean emulator;

    void accept(Listener listener) {
      listener.onDeviceRemoved(this);
    }
  }

  private static final Gson GSON = new Gson();
  private static final Logger LOG = Logger.getInstance(DaemonEvent.class.getName());
}

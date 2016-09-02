/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.gson.*;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Manage the flutter daemon.
 * TODO Restructure process management to allow the daemon to run forever.
 * Currently it will be started when the debug session starts, and it ends
 * when the debug session ends. The obvious disadvantage to this is that
 * it takes some time to discover connected devices when the daemon is started.
 * Making this change would allow users to easily work with multiple devices.
 * http://c2.com/cgi/wiki?MakeItWorkMakeItRightMakeItFast
 */
public class DaemonManager extends ProcessAdapter {
  private static final Logger LOG = Logger.getInstance(DaemonManager.class.getName());
  private static final String STARTING_DAEMON = "Starting device daemon...";
  private static final String STDOUT_KEY = "stdout";
  private static final Gson GSON = new Gson();

  final private Project myProject;
  private int myCommandId = 0;
  private Map<Integer, Method> myCommands = new HashMap<>();
  private String myCurrentJsonText;
  private ProcessHandler myHandler;
  private Map<String, DeviceAdded> myDevices = new HashMap<>();
  private Map<String, AppStarted> myApps = new HashMap<>();
  private Map<String, Integer> debugPorts = new HashMap<>();

  public DaemonManager(Project project) {
    myProject = project;
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    if (outputType.toString().equals(STDOUT_KEY)) {
      String text = event.getText().trim();
      if (text.startsWith("[{") && text.endsWith("}]")) {
        text = text.substring(1, text.length() - 1);
        processEventText(text, event.getProcessHandler());
      }
      else if (text.startsWith(STARTING_DAEMON)) {
        enableDevicePolling(event.getProcessHandler());
      }
    }
  }

  public boolean isConnectionReady(FlutterDaemonRunState state) {
    // TODO Identify which app from state.
    return !debugPorts.isEmpty();
  }

  public int getPort(FlutterDaemonRunState state) {
    // TODO Identify which app from state.
    return debugPorts.values().iterator().next();
  }

  private void enableDevicePolling(@NotNull ProcessHandler handler) {
    sendCommand(handler, "device.enable", null);
  }

  private void eventLogMessage(LogMessage log) {
  }

  private void eventLogMessage(AppLog log) {
  }

  private void eventDeviceAdded(DeviceAdded added) {
    myDevices.put(added.id, added);
    if (myDevices.size() == 1) {
      AppStart params = new AppStart(added.id, ".");
      sendCommand(myHandler, "app.start", params);
    }
  }

  private void eventDeviceRemoved(DeviceRemoved removed) {
    myDevices.remove(removed.id);
  }

  private void eventAppStarted(AppStarted started) {
    myApps.put(started.appId, started);
  }

  private void eventAppStopped(AppStopped stopped) {
    myApps.remove(stopped.appId);
  }

  private void eventDebugPort(AppDebugPort debugPort) {
    debugPorts.put(debugPort.appId, debugPort.port);
  }

  private void sendCommand(@NotNull ProcessHandler handler, String command, Params params) {
    OutputStream input = handler.getProcessInput();
    if (input == null) {
      LOG.error("No process input");
      return;
    }
    int cmdId = myCommandId++;
    Method method = new Method(command, params, cmdId);
    try (FlutterStream str = new FlutterStream(input)) {
      str.print("[");
      str.print(GSON.toJson(method));
      str.println("]");
      myCommands.put(cmdId, method);
    }
  }

  private void processEventText(final String text, @NotNull ProcessHandler handler) {
    myCurrentJsonText = text;
    try {
      JsonParser jp = new JsonParser();
      JsonElement elem = jp.parse(text);
      dispatch(elem.getAsJsonObject(), handler);
    }
    catch (JsonSyntaxException ex) {
      LOG.error(ex);
    }
  }

  private void dispatch(JsonObject obj, @NotNull ProcessHandler handler) {
    this.myHandler = handler;
    JsonPrimitive primId = obj.getAsJsonPrimitive("id");
    if (primId == null) {
      // event
      JsonPrimitive primEvent = obj.getAsJsonPrimitive("event");
      if (primEvent == null) {
        LOG.error("Invalid JSON from flutter: " + myCurrentJsonText);
        return;
      }
      String eventName = primEvent.getAsString();
      JsonObject params = obj.getAsJsonObject("params");
      if (eventName == null || params == null) {
        LOG.error("Bad event from flutter: " + myCurrentJsonText);
        return;
      }
      Event eventHandler = eventHandler(eventName);
      if (eventHandler == null) return;
      eventHandler.from(params).process(this);
    }
    else {
      // response
      int responseId = primId.getAsInt();
      Method method = myCommands.get(responseId);
      if (method != null) {
        method.process(obj, this);
      }
    }
  }

  private Event eventHandler(String eventName) {
    switch (eventName) {
      case "device.added":
        return new DeviceAdded();
      case "app.start":
        return new AppStarted();
      case "app.debugPort":
        return new AppDebugPort();
      case "app.log":
        return new AppLog();
      case "daemon.logMessage":
        return new LogMessage();
      case "app.stop":
        return new AppStopped();
      case "device.removed":
        return new DeviceRemoved();
      default:
        LOG.error("Unknown flutter event: " + eventName + " from json: " + myCurrentJsonText);
        return null;
    }
  }

  private abstract static class FlutterJsonObject {
    // Placeholder for any abstractions applicable to all JSON to/from Flutter.
  }

  private static class Method extends FlutterJsonObject {

    Method(String method, Params params, int cmdId) {
      this.method = method;
      this.params = params;
      this.id = cmdId;
    }

    @SuppressWarnings("unused") private String method;
    @SuppressWarnings("unused") private Params params;
    @SuppressWarnings("unused") private int id;

    void process(JsonObject obj, DaemonManager manager) {
      if (params != null) params.process(obj, manager);
    }
  }

  private abstract static class Params extends FlutterJsonObject {

    abstract void process(JsonObject obj, DaemonManager manager);
  }

  private static class AppStart extends Params {
    // "method":"app.start"
    AppStart(String deviceId, String projectDirectory) {
      this.deviceId = deviceId;
      this.projectDirectory = projectDirectory;
    }

    @SuppressWarnings("unused") private String deviceId;
    @SuppressWarnings("unused") private String projectDirectory;
    @SuppressWarnings("unused") private boolean startPaused = true;
    @SuppressWarnings("unused") private String route;
    @SuppressWarnings("unused") private String mode = "debug";
    @SuppressWarnings("unused") private String target;
    @SuppressWarnings("unused") private boolean hot = false;

    void process(JsonObject obj, DaemonManager manager) {
      JsonObject result = obj.getAsJsonObject("result");
      AppStarted app = new AppStarted();
      app.appId = result.getAsJsonPrimitive("appId").getAsString();
      app.deviceId = result.getAsJsonPrimitive("deviceId").getAsString();
      app.directory = result.getAsJsonPrimitive("directory").getAsString();
      app.supportsRestart = result.getAsJsonPrimitive("supportsRestart").getAsBoolean();
      app.process(manager);
    }
  }

  private static class DeviceForward extends Params {
    // "method":"device.forward"
    DeviceForward(String deviceId, int devicePort, int hostPort) {
      this.deviceId = deviceId;
      this.devicePort = devicePort;
      this.hostPort = hostPort;
    }

    @SuppressWarnings("unused") private String deviceId;
    @SuppressWarnings("unused") private int devicePort;
    @SuppressWarnings("unused") private int hostPort;

    void process(JsonObject obj, DaemonManager manager) {
      JsonObject result = obj.getAsJsonObject("result");
    }
  }

  private abstract static class Event extends FlutterJsonObject {

    Event from(JsonElement element) {
      return GSON.fromJson(element, (Type)this.getClass());
    }

    abstract void process(DaemonManager manager);
  }

  private static class LogMessage extends Event {
    // "event":"daemon.eventLogMessage"
    @SuppressWarnings("unused") private String level;
    @SuppressWarnings("unused") private String message;
    @SuppressWarnings("unused") private String stackTrace;

    void process(DaemonManager manager) {
      manager.eventLogMessage(this);
    }
  }

  private static class AppLog extends Event {
    // "event":"app.log"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String log;

    void process(DaemonManager manager) {
      manager.eventLogMessage(this);
    }
  }

  private static class DeviceAdded extends Event {
    // "event":"device.added"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;

    void process(DaemonManager manager) {
      manager.eventDeviceAdded(this);
    }
  }

  private static class DeviceRemoved extends Event {
    // "event":"device.removed"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;

    void process(DaemonManager manager) {
      manager.eventDeviceRemoved(this);
    }
  }

  private static class AppStarted extends Event {
    // "event":"app.start"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String deviceId;
    @SuppressWarnings("unused") private String directory;
    @SuppressWarnings("unused") private boolean supportsRestart;

    void process(DaemonManager manager) {
      manager.eventAppStarted(this);
    }
  }

  private static class AppStopped extends Event {
    // "event":"app.stop"
    @SuppressWarnings("unused") private String appId;

    void process(DaemonManager manager) {
      manager.eventAppStopped(this);
    }
  }

  private static class AppDebugPort extends Event {
    // "event":"app.eventDebugPort"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private int port;

    void process(DaemonManager manager) {
      manager.eventDebugPort(this);
    }
  }

  private static class FlutterStream extends PrintStream {

    public FlutterStream(@NotNull OutputStream out) {
      super(out);
    }

    @Override
    public void close() {
      // Closing the stream terminates the process, so don't do it.
      flush();
    }
  }
}

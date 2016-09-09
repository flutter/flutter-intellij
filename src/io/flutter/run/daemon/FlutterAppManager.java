/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keeper of running Flutter apps.
 */
@SuppressWarnings("Duplicates") // TODO remove after deleting DaemonManager
public class FlutterAppManager {
  private static final Logger LOG = Logger.getInstance("#io.flutter.run.daemon.FlutterAppManager");

  private static final Gson GSON = new Gson();

  private List<FlutterApp> myApps = new ArrayList<>();
  private Map<Integer, List<Command>> myPendingCommands = new THashMap<>();

  public FlutterApp startApp(@NotNull FlutterDaemonService service,
                             @NotNull FlutterDaemonController controller,
                             @NotNull String deviceId,
                             @NotNull RunMode mode,
                             boolean isPaused,
                             boolean isHot,
                             @Nullable String target,
                             @Nullable String route) {
    RunningFlutterApp app = new RunningFlutterApp(service, controller, this, mode, isHot, target, route);
    // TODO start app on controller, wait for response, set AppStarted to app.setApp()
    myApps.add(app);
    return app;
  }

  void handleResponse(int cmdId, JsonObject obj, FlutterDaemonController controller) {
    Command cmd = findPendingCmd(cmdId, controller);
    removePendingCmd(cmdId, cmd);
    cmd.method.process(obj, this, controller);
  }

  void handleEvent(JsonObject obj, FlutterDaemonController controller, String json) {
    JsonPrimitive primEvent = obj.getAsJsonPrimitive("event");
    if (primEvent == null) {
      LOG.error("Invalid JSON from flutter: " + json);
      return;
    }
    String eventName = primEvent.getAsString();
    JsonObject params = obj.getAsJsonObject("params");
    if (eventName == null || params == null) {
      LOG.error("Bad event from flutter: " + json);
      return;
    }
    Event eventHandler = eventHandler(eventName, json);
    if (eventHandler == null) return;
    eventHandler.from(params).process(this, controller);
  }

  void stopApp(RunningFlutterApp app) {
    // TODO send app.stop command
    myApps.remove(app);
    app.getController().removeDeviceId(app.deviceId());
  }

  void restartApp(RunningFlutterApp app, boolean isFullRestart) {
    // TODO send app.restart command
  }

  @NotNull
  private Command findPendingCmd(int id, FlutterDaemonController controller) {
    List<Command> list = myPendingCommands.get(id);
    for (Command cmd : list) {
      if (cmd.controller == controller) return cmd;
    }
    throw new IllegalStateException("no matching pending command");
  }

  private void removePendingCmd(int id, Command command) {
    List<Command> list = myPendingCommands.get(id);
    list.remove(command);
    if (list.isEmpty()) {
      myPendingCommands.remove(id);
    }
  }

  private void addPendingCmd(int id, Command command) {
    List<Command> list = myPendingCommands.get(id);
    if (list == null) {
      list = new ArrayList<>();
      myPendingCommands.put(id, list);
    }
    list.add(command);
  }

  private static Event eventHandler(String eventName, String json) {
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
        LOG.error("Unknown flutter event: " + eventName + " from json: " + json);
        return null;
    }
  }

  private void eventLogMessage(LogMessage message, FlutterDaemonController controller) {
  }

  private void eventLogMessage(AppLog message, FlutterDaemonController controller) {
  }

  private void eventDeviceAdded(DeviceAdded added, FlutterDaemonController controller) {
  }

  private void eventDeviceRemoved(DeviceRemoved removed, FlutterDaemonController controller) {
  }

  private void eventAppStarted(AppStarted started, FlutterDaemonController controller) {
  }

  private void eventAppStopped(AppStopped stopped, FlutterDaemonController controller) {
  }

  private void eventDebugPort(AppDebugPort port, FlutterDaemonController controller) {
  }

  private static class Command {
    Method method;
    FlutterDaemonController controller;
    Command(Method method, FlutterDaemonController controller) {
      this.method = method;
      this.controller = controller;
    }
  }


  private abstract static class FlutterJsonObject {
    // Placeholder for any abstractions applicable to all JSON to/from Flutter.
    // ALL field names in subclasses are defined by the JSON protocol used by Flutter.
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

    void process(JsonObject obj, FlutterAppManager manager, FlutterDaemonController controller) {
      if (params != null) params.process(obj, manager, controller);
    }
  }

  private abstract static class Params extends FlutterJsonObject {

    abstract void process(JsonObject obj, FlutterAppManager manager, FlutterDaemonController controller);
  }

  private static class AppStart extends Params {
    // "method":"app.start"
    AppStart(String deviceId, String projectDirectory, boolean startPaused, String route, String mode, String target, boolean hot) {
      this.deviceId = deviceId;
      this.projectDirectory = projectDirectory;
      this.startPaused = startPaused;
      this.route = route;
      this.mode = mode;
      this.target = target;
      this.hot = hot;
    }

    @SuppressWarnings("unused") private String deviceId;
    @SuppressWarnings("unused") private String projectDirectory;
    @SuppressWarnings("unused") private boolean startPaused = true;
    @SuppressWarnings("unused") private String route;
    @SuppressWarnings("unused") private String mode;
    @SuppressWarnings("unused") private String target;
    @SuppressWarnings("unused") private boolean hot;

    void process(JsonObject obj, FlutterAppManager manager, FlutterDaemonController controller) {
      JsonObject result = obj.getAsJsonObject("result");
      AppStarted app = new AppStarted();
      app.appId = result.getAsJsonPrimitive("appId").getAsString();
      app.deviceId = result.getAsJsonPrimitive("deviceId").getAsString();
      app.directory = result.getAsJsonPrimitive("directory").getAsString();
      app.supportsRestart = result.getAsJsonPrimitive("supportsRestart").getAsBoolean();
      manager.eventAppStarted(app, controller);
    }
  }

  private abstract static class Event extends FlutterJsonObject {

    Event from(JsonElement element) {
      return GSON.fromJson(element, (Type)this.getClass());
    }

    abstract void process(FlutterAppManager manager, FlutterDaemonController controller);
  }

  private static class LogMessage extends Event {
    // "event":"daemon.eventLogMessage"
    @SuppressWarnings("unused") private String level;
    @SuppressWarnings("unused") private String message;
    @SuppressWarnings("unused") private String stackTrace;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventLogMessage(this, controller);
    }
  }

  private static class AppLog extends Event {
    // "event":"app.log"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String log;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventLogMessage(this, controller);
    }
  }

  private static class DeviceAdded extends Event {
    // "event":"device.added"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDeviceAdded(this, controller);
    }
  }

  private static class DeviceRemoved extends Event {
    // "event":"device.removed"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDeviceRemoved(this, controller);
    }
  }

  static class AppStarted extends Event {
    // "event":"app.start"
    @SuppressWarnings("unused") String appId;
    @SuppressWarnings("unused") String deviceId;
    @SuppressWarnings("unused") String directory;
    @SuppressWarnings("unused") boolean supportsRestart;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      // This event is ignored. The app.start command response is used instead.
    }
  }

  private static class AppStopped extends Event {
    // "event":"app.stop"
    @SuppressWarnings("unused") private String appId;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventAppStopped(this, controller);
    }
  }

  private static class AppDebugPort extends Event {
    // "event":"app.eventDebugPort"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private int port;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDebugPort(this, controller);
    }
  }
}

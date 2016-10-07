/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.gson.*;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.TimeoutUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Keeper of running Flutter apps.
 * TODO(messick) Clean up myResponses as things change
 */
public class FlutterAppManager {

  private static final String CMD_APP_START = "app.start";
  private static final String CMD_APP_STOP = "app.stop";
  private static final String CMD_APP_RESTART = "app.restart";
  private static final String CMD_DEVICE_ENABLE = "device.enable";

  private static final Gson GSON = new Gson();

  private Logger myLogger = Logger.getInstance("#io.flutter.run.daemon.FlutterAppManager");
  private FlutterDaemonService myService;
  private List<FlutterApp> myApps = new ArrayList<>();
  private Map<Integer, List<Command>> myPendingCommands = new THashMap<>();
  private int myCommandId = 0;
  private final Object myLock = new Object();
  private Map<Method, FlutterJsonObject> myResponses = new THashMap<>();
  private ProgressHandler myProgressHandler;

  FlutterAppManager(@NotNull FlutterDaemonService service) {
    this.myService = service;
  }

  @Nullable
  public FlutterApp startApp(@NotNull FlutterDaemonController controller,
                             @NotNull String deviceId,
                             @NotNull RunMode mode,
                             @NotNull Project project,
                             boolean isPaused,
                             boolean isHot,
                             @Nullable String target) {
    if (isAppRunning(deviceId, controller)) {
      throw new ProcessCanceledException();
    }
    myProgressHandler = new ProgressHandler(project);
    if (!waitForDevice(deviceId)) {
      return null;
    }
    FlutterDaemonService service;
    synchronized (myLock) {
      service = myService;
    }
    RunningFlutterApp app = new RunningFlutterApp(service, controller, this, mode, project, isHot, target, null);
    AppStart appStart = new AppStart(deviceId, controller.getProjectDirectory(), isPaused, null, mode.mode(), target, isHot);
    Method cmd = makeMethod(CMD_APP_START, appStart);
    CompletableFuture
      .supplyAsync(() -> sendCommand(controller, cmd))
      .thenApplyAsync(this::waitForResponse)
      .thenAcceptAsync((started) -> {
        if (started instanceof AppStarted) {
          AppStarted appStarted = (AppStarted)started;
          app.setApp(appStarted);
        }
      });
    synchronized (myLock) {
      myApps.add(app);
    }
    return app;
  }

  private boolean isAppRunning(String deviceId, FlutterDaemonController controller) {
    Stream<FlutterApp> apps;
    synchronized (myLock) {
      apps = myApps.stream();
    }
    return apps.anyMatch((app) -> app.getController() == controller && app.deviceId().equals(deviceId));
  }

  private boolean waitForDevice(@NotNull String deviceId) {
    long timeout = 5000L;
    Boolean[] resp = {false};
    TimeoutUtil.executeWithTimeout(timeout, () -> {
      while (!resp[0]) {
        Stream<ConnectedDevice> stream;
        synchronized (myLock) {
          stream = myService.getConnectedDevices().stream();
        }
        if (stream.anyMatch(d -> d.deviceId().equals(deviceId))) {
          resp[0] = true;
        }
      }
    });
    return resp[0];
  }

  @Nullable
  private RunningFlutterApp waitForApp(@NotNull FlutterDaemonController controller, @NotNull String appId) {
    long timeout = 10000L;
    RunningFlutterApp[] resp = {null};
    TimeoutUtil.executeWithTimeout(timeout, () -> {
      while (resp[0] == null) {
        RunningFlutterApp app = findApp(controller, appId);
        if (app != null) {
          resp[0] = app;
        }
      }
    });
    return resp[0];
  }

  @Nullable
  private FlutterJsonObject waitForResponse(@NotNull Method cmd) {
    return waitForResponse(cmd, 10000L);
  }

  @Nullable
  private FlutterJsonObject waitForResponse(@NotNull Method cmd, final long timeout) {
    FlutterJsonObject[] resp = {null};
    TimeoutUtil.executeWithTimeout(timeout, () -> {
      while (resp[0] == null) {
        synchronized (myLock) {
          resp[0] = myResponses.get(cmd);
          if (resp[0] != null) {
            myResponses.remove(cmd);
          }
        }
      }
    });
    return resp[0];
  }

  void startDevicePoller(@NotNull FlutterDaemonController pollster) {
    final Project project = null;
    try {
      pollster.forkProcess(project);
    }
    catch (ExecutionException e) {
      myLogger.error(e);
    }
  }

  void processInput(@NotNull String string, @NotNull FlutterDaemonController controller) {
    try {
      JsonParser jp = new JsonParser();
      JsonElement elem = jp.parse(string);
      JsonObject obj = elem.getAsJsonObject();
      JsonPrimitive primId = obj.getAsJsonPrimitive("id");
      if (primId == null) {
        handleEvent(obj, controller, string);
      }
      else {
        handleResponse(primId.getAsInt(), obj, controller);
      }
    }
    catch (JsonSyntaxException ex) {
      myLogger.error(ex);
    }
  }

  void handleResponse(int cmdId, @NotNull JsonObject obj, @NotNull FlutterDaemonController controller) {
    Command cmd = findPendingCmd(cmdId, controller);
    try {
      cmd.method.process(obj, this, controller);
    }
    finally {
      removePendingCmd(cmdId, cmd);
    }
  }

  void handleEvent(@NotNull JsonObject obj, @NotNull FlutterDaemonController controller, @NotNull String json) {
    JsonPrimitive primEvent = obj.getAsJsonPrimitive("event");
    if (primEvent == null) {
      myLogger.error("Invalid JSON from flutter: " + json);
      return;
    }
    String eventName = primEvent.getAsString();
    JsonObject params = obj.getAsJsonObject("params");
    if (eventName == null || params == null) {
      myLogger.error("Bad event from flutter: " + json);
      return;
    }
    Event eventHandler = eventHandler(eventName, json);
    if (eventHandler == null) return;
    eventHandler.from(params).process(this, controller);
  }

  void stopApp(@NotNull FlutterApp app) {
    myProgressHandler.cancel();
    if (app.hasAppId()) {
      if (app.isSessionPaused()) {
        app.forceResume();
      }
      AppStop appStop = new AppStop(app.appId());
      Method cmd = makeMethod(CMD_APP_STOP, appStop);
      // This needs to run synchronously. The next thing that happens is the process
      // streams are closed which immediately terminates the process.
      CompletableFuture
        .completedFuture(sendCommand(app.getController(), cmd))
        .thenApply(this::waitForResponse)
        .thenAccept((stopped) -> {
          synchronized (myLock) {
            myApps.remove(app);
          }
          app.getController().removeDeviceId(app.deviceId());
        });
    }
  }

  void restartApp(@NotNull RunningFlutterApp app, boolean isFullRestart) {
    AppRestart appStart = new AppRestart(app.appId(), isFullRestart);
    Method cmd = makeMethod(CMD_APP_RESTART, appStart);
    sendCommand(app.getController(), cmd);
  }

  private void appStopped(AppStop started, FlutterDaemonController controller) {
    Stream<Command> starts = findAllPendingCmds(controller).stream().filter(c -> {
      Params p = c.method.params;
      if (p instanceof AppStop) {
        AppStop a = (AppStop)p;
        if (a.appId.equals(started.appId)) return true;
      }
      return false;
    });
    Optional<Command> opt = starts.findFirst();
    assert (opt.isPresent());
    Command cmd = opt.get();
    synchronized (myLock) {
      myResponses.put(cmd.method, started);
      myService.schedulePolling();
    }
  }

  void enableDevicePolling(@NotNull FlutterDaemonController controller) {
    Method method = makeMethod(CMD_DEVICE_ENABLE, null);
    sendCommand(controller, method);
  }

  void aboutToTerminateAll(FlutterDaemonController controller) {
    List<FlutterApp> apps;
    synchronized (myLock) {
      apps = new ArrayList<>(myApps);
    }
    for (FlutterApp app : apps) {
      if (app.getController() == controller) {
        stopApp(app);
      }
    }
  }

  @NotNull
  private Method makeMethod(@NotNull String methodName, @Nullable Params params) {
    return new Method(methodName, params, myCommandId++);
  }

  private Method sendCommand(@NotNull FlutterDaemonController controller, @NotNull Method method) {
    controller.sendCommand(GSON.toJson(method), this);
    addPendingCmd(method.id, new Command(method, controller));
    return method;
  }

  @NotNull
  private List<Command> findAllPendingCmds(@NotNull FlutterDaemonController controller) {
    List<Command> result = new ArrayList<>();
    for (List<Command> list : myPendingCommands.values()) {
      for (Command cmd : list) {
        if (cmd.controller == controller) result.add(cmd);
      }
    }
    return result;
  }

  @NotNull
  private Command findPendingCmd(int id, @NotNull FlutterDaemonController controller) {
    List<Command> list = myPendingCommands.get(id);
    for (Command cmd : list) {
      if (cmd.controller == controller) return cmd;
    }
    throw new IllegalStateException("no matching pending command");
  }

  private void removePendingCmd(int id, @NotNull Command command) {
    List<Command> list = myPendingCommands.get(id);
    list.remove(command);
    if (list.isEmpty()) {
      myPendingCommands.remove(id);
    }
  }

  private void addPendingCmd(int id, @NotNull Command command) {
    List<Command> list = myPendingCommands.get(id);
    if (list == null) {
      list = new ArrayList<>();
      myPendingCommands.put(id, list);
    }
    list.add(command);
  }

  @Nullable
  private Event eventHandler(@NotNull String eventName, @Nullable String json) {
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
        myLogger.error("Unknown flutter event: " + eventName + " from json: " + json);
        return null;
    }
  }

  private void eventLogMessage(@NotNull LogMessage message, @NotNull FlutterDaemonController controller) {
    myLogger.info(message.message);
  }

  private void eventLogMessage(@NotNull AppLog message, @NotNull FlutterDaemonController controller) {
    RunningFlutterApp app = findApp(controller, message.appId);

    if (app == null) {
      return;
    }

    if (message.progress) {
      if (message.finished) {
        myProgressHandler.done();
      }
      else {
        myProgressHandler.start(message.log);
      }
    }
    else {
      app.getConsole().print(message.log + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  private void eventDeviceAdded(@NotNull DeviceAdded added, @NotNull FlutterDaemonController controller) {
    synchronized (myLock) {
      myService.addConnectedDevice(new FlutterDevice(added.name, added.id, added.platform, added.emulator));
    }
  }

  private void eventDeviceRemoved(@NotNull DeviceRemoved removed, @NotNull FlutterDaemonController controller) {
    synchronized (myLock) {
      for (ConnectedDevice device : myService.getConnectedDevices()) {
        if (device.deviceName().equals(removed.name) &&
            device.deviceId().equals(removed.id) &&
            device.platform().equals(removed.platform)) {
          myService.removeConnectedDevice(device);
        }
      }
    }
  }

  private void eventAppStarted(@NotNull AppStarted started, @NotNull FlutterDaemonController controller) {
    assert started.directory.equals(controller.getProjectDirectory());
    Stream<Command> starts = findAllPendingCmds(controller).stream().filter(c -> {
      Params p = c.method.params;
      if (p instanceof AppStart) {
        AppStart a = (AppStart)p;
        if (a.deviceId.equals(started.deviceId)) return true;
      }
      return false;
    });
    Optional<Command> opt = starts.findFirst();
    assert (opt.isPresent());
    Command cmd = opt.get();
    synchronized (myLock) {
      myResponses.put(cmd.method, started);
    }
  }

  private void eventAppStopped(@NotNull AppStopped stopped, @NotNull FlutterDaemonController controller) {
    // TODO(devoncarew): Terminate the launch.

    myProgressHandler.cancel();
  }

  private void eventDebugPort(@NotNull AppDebugPort port, @NotNull FlutterDaemonController controller) {
    RunningFlutterApp app = waitForApp(controller, port.appId);
    if (app != null) {
      app.setPort(port.port);
      app.setBaseUri(port.baseUri);
    }
  }

  private void error(JsonObject json) {
    System.out.println(json.toString()); // TODO error handling
  }

  private RunningFlutterApp findApp(FlutterDaemonController controller, String appId) {
    synchronized (myLock) {
      for (FlutterApp app : myApps) {
        if (app.getController() == controller && app.hasAppId() && app.appId().equals(appId)) {
          return (RunningFlutterApp)app;
        }
      }
    }
    return null;
  }

  private static class Command {
    @NotNull Method method;
    @NotNull FlutterDaemonController controller;

    Command(@NotNull Method method, @NotNull FlutterDaemonController controller) {
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
      if (result == null) {
        manager.error(obj);
        return;
      }
      AppStarted app = new AppStarted();
      app.appId = result.getAsJsonPrimitive("appId").getAsString();
      app.deviceId = result.getAsJsonPrimitive("deviceId").getAsString();
      app.directory = result.getAsJsonPrimitive("directory").getAsString();
      app.supportsRestart = result.getAsJsonPrimitive("supportsRestart").getAsBoolean();
      manager.eventAppStarted(app, controller);
    }
  }

  private static class AppRestart extends Params {
    // "method":"app.restart"
    AppRestart(String appId, boolean fullRestart) {
      this.appId = appId;
      this.fullRestart = fullRestart;
    }

    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private boolean fullRestart;

    void process(JsonObject obj, FlutterAppManager manager, FlutterDaemonController controller) {
    }
  }

  private static class AppStop extends Params {
    // "method":"app.stop"
    AppStop(String appId) {
      this.appId = appId;
    }

    @SuppressWarnings("unused") private String appId;

    void process(JsonObject obj, FlutterAppManager manager, FlutterDaemonController controller) {
      JsonPrimitive prim = obj.getAsJsonPrimitive("result");
      if (prim != null) {
        if (prim.getAsBoolean()) {
          manager.appStopped(this, controller);
        }
      }
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
    @SuppressWarnings("unused") private boolean progress;
    @SuppressWarnings("unused") private boolean finished;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventLogMessage(this, controller);
    }
  }

  private static class DeviceAdded extends Event {
    // "event":"device.added"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;
    @SuppressWarnings("unused") private boolean emulator;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDeviceAdded(this, controller);
    }
  }

  private static class DeviceRemoved extends Event {
    // "event":"device.removed"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;
    @SuppressWarnings("unused") private boolean emulator;

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
    @SuppressWarnings("unused") private String baseUri;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDebugPort(this, controller);
    }
  }
}

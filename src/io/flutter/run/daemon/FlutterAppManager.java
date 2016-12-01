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

import java.io.File;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeper of running Flutter apps.
 *
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
  private StopWatch myProgressStopWatch;

  FlutterAppManager(@NotNull FlutterDaemonService service) {
    this.myService = service;
  }

  @Nullable
  public FlutterApp startApp(@NotNull FlutterDaemonController controller,
                             @NotNull String deviceId,
                             @NotNull RunMode mode,
                             @NotNull Project project,
                             boolean startPaused,
                             boolean isHot,
                             @Nullable String target) {
    if (isAppRunning(deviceId, controller)) {
      throw new ProcessCanceledException();
    }
    myProgressHandler = new ProgressHandler(project);
    // TODO(devoncarew): We don't need this call.
    if (!waitForDevice(deviceId)) {
      return null;
    }
    FlutterDaemonService service;
    synchronized (myLock) {
      service = myService;
    }
    RunningFlutterApp app = new RunningFlutterApp(service, controller, this, mode, project, isHot, target, null);
    app.changeState(FlutterApp.State.STARTING);
    AppStart appStart = new AppStart(deviceId, controller.getProjectDirectory(), startPaused, null, mode.mode(), target, isHot);
    Method cmd = makeMethod(CMD_APP_START, appStart);
    CompletableFuture
      .supplyAsync(() -> sendCommand(controller, cmd))
      .thenApplyAsync(this::waitForResponse)
      .thenAcceptAsync((started) -> {
        if (started instanceof AppStartEvent) {
          AppStartEvent appStarted = (AppStartEvent)started;
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
    return apps.anyMatch((app) -> app.getController() == controller && app.hasAppId() && app.deviceId().equals(deviceId));
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
    final long timeout = 10000L;
    FlutterJsonObject[] resp = {null};
    try {
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
    }
    catch (ThreadDeath ex) {
      // Can happen if external process is killed, but we don't care.
    }
    return resp[0];
  }

  void startDevicePoller(@NotNull FlutterDaemonController pollster) {
    try {
      pollster.startDevicePoller();
    }
    catch (ExecutionException e) {
      // User notification comes in the way of editor toasts (see IncompatibleDartPluginNotification).
      myLogger.warn(e);
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
      app.changeState(FlutterApp.State.TERMINATING);
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

  void restartApp(@NotNull RunningFlutterApp app, boolean isFullRestart, boolean pauseAfterRestart) {
    AppRestart appStart = new AppRestart(app.appId(), isFullRestart, pauseAfterRestart);
    app.changeState(FlutterApp.State.STARTING);
    Method cmd = makeMethod(CMD_APP_RESTART, appStart);
    sendCommand(app.getController(), cmd);
  }

  private void appStopped(AppStop stopped, FlutterDaemonController controller) {
    Stream<Command> starts = findAllPendingCmds(controller).stream().filter(c -> {
      Params p = c.method.params;
      if (p instanceof AppStop) {
        AppStop a = (AppStop)p;
        if (a.appId.equals(stopped.appId)) return true;
      }
      return false;
    });
    Optional<Command> opt = starts.findFirst();
    assert (opt.isPresent());
    RunningFlutterApp app = findApp(controller, stopped.appId);
    if (app != null) {
      app.changeState(FlutterApp.State.TERMINATED);
    }
    Command cmd = opt.get();
    synchronized (myLock) {
      myResponses.put(cmd.method, stopped);
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
    apps.stream().filter(app -> app.getController() == controller).forEach(this::stopApp);
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
      result.addAll(list.stream().filter(cmd -> cmd.controller == controller).collect(Collectors.toList()));
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
    List<Command> list = myPendingCommands.computeIfAbsent(id, k -> new ArrayList<>());
    list.add(command);
  }

  @Nullable
  private Event eventHandler(@NotNull String eventName, @Nullable String json) {
    switch (eventName) {
      case "device.added":
        return new DeviceAddedEvent();
      case "device.removed":
        return new DeviceRemovedEvent();
      case "app.start":
        return new AppStartEvent();
      case "app.started":
        return new AppStartedEvent();
      case "app.debugPort":
        return new AppDebugPortEvent();
      case "app.log":
        return new AppLogEvent();
      case "app.progress":
        return new AppProgressEvent();
      case "app.stop":
        return new AppStoppedEvent();
      case "daemon.logMessage":
        return new LogMessageEvent();
      default:
        return null;
    }
  }

  private void eventLogMessage(@NotNull LogMessageEvent message, @NotNull FlutterDaemonController controller) {
    myLogger.info(message.message);
  }

  private void eventLogMessage(@NotNull AppLogEvent message, @NotNull FlutterDaemonController controller) {
    RunningFlutterApp app = findApp(controller, message.appId);

    if (app == null) {
      return;
    }

    app.getConsole().print(message.log + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  private void eventProgressMessage(@NotNull AppProgressEvent message, @NotNull FlutterDaemonController controller) {
    RunningFlutterApp app = findApp(controller, message.appId);

    if (app == null) {
      return;
    }

    if (message.finished) {
      myProgressHandler.done();

      if (myProgressStopWatch != null) {
        myProgressStopWatch.stop();

        if (message.progressId != null && message.progressId.startsWith("hot.")) {
          if (message.progressId.equals("hot.reload")) {
            app.getConsole().print("\nReloaded in " + myProgressStopWatch.getTime() + "ms.\n", ConsoleViewContentType.NORMAL_OUTPUT);
          }
          else if (message.progressId.equals("hot.restart")) {
            app.getConsole().print("\nRestarted in " + myProgressStopWatch.getTime() + "ms.\n", ConsoleViewContentType.NORMAL_OUTPUT);
          }
        }

        myProgressStopWatch = null;
      }
    }
    else {
      myProgressHandler.start(message.message);

      if (message.progressId != null && message.progressId.startsWith("hot.")) {
        myProgressStopWatch = new StopWatch();
        myProgressStopWatch.start();
      }
    }
  }

  private void eventDeviceAdded(@NotNull DeviceAddedEvent added, @NotNull FlutterDaemonController controller) {
    synchronized (myLock) {
      myService.addConnectedDevice(new FlutterDevice(added.name, added.id, added.platform, added.emulator));
    }
  }

  private void eventDeviceRemoved(@NotNull DeviceRemovedEvent removed, @NotNull FlutterDaemonController controller) {
    synchronized (myLock) {
      myService.getConnectedDevices().stream().filter(device -> device.deviceName().equals(removed.name) &&
                                                                device.deviceId().equals(removed.id) &&
                                                                device.platform().equals(removed.platform))
        .forEach(device -> myService.removeConnectedDevice(device));
    }
  }

  private void eventAppStarted(@NotNull AppStartEvent started, @NotNull FlutterDaemonController controller) {
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

  private void eventAppStopped(@NotNull AppStoppedEvent stopped, @NotNull FlutterDaemonController controller) {
    // TODO(devoncarew): Terminate the launch.

    myProgressHandler.cancel();
  }

  private void eventDebugPort(@NotNull AppDebugPortEvent port, @NotNull FlutterDaemonController controller) {
    RunningFlutterApp app = waitForApp(controller, port.appId);
    if (app != null) {
      app.setPort(port.port);

      String uri = port.baseUri;
      if (uri != null) {
        if (uri.startsWith("file:")) {
          // Convert the file: url to a path.
          try {
            uri = new URL(uri).getPath();
            if (uri.endsWith(File.separator)) {
              uri = uri.substring(0, uri.length() - 1);
            }
          }
          catch (MalformedURLException e) {
            // ignore
          }
        }
        app.setBaseUri(uri);
      }
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
      AppStartEvent app = new AppStartEvent();
      app.appId = result.getAsJsonPrimitive("appId").getAsString();
      app.deviceId = result.getAsJsonPrimitive("deviceId").getAsString();
      app.directory = result.getAsJsonPrimitive("directory").getAsString();
      app.supportsRestart = result.getAsJsonPrimitive("supportsRestart").getAsBoolean();
      manager.eventAppStarted(app, controller);
    }
  }

  private static class AppRestart extends Params {
    // "method":"app.restart"
    AppRestart(String appId, boolean fullRestart, boolean pause) {
      this.appId = appId;
      this.fullRestart = fullRestart;
      this.pause = pause;
    }

    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private boolean fullRestart;
    @SuppressWarnings("unused") private boolean pause;

    void process(JsonObject obj, FlutterAppManager manager, FlutterDaemonController controller) {
      RunningFlutterApp app = manager.findApp(controller, appId);
      assert app != null;
      app.changeState(FlutterApp.State.STARTED);
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
      else {
        prim = obj.getAsJsonPrimitive("error");
        if (prim != null) {
          // Apparently the daemon does not find apps started in release mode.
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

  private static class LogMessageEvent extends Event {
    // "event":"daemon.eventLogMessage"
    @SuppressWarnings("unused") private String level;
    @SuppressWarnings("unused") private String message;
    @SuppressWarnings("unused") private String stackTrace;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventLogMessage(this, controller);
    }
  }

  private static class AppLogEvent extends Event {
    // "event":"app.log"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String log;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventLogMessage(this, controller);
    }
  }

  private static class AppProgressEvent extends Event {
    // "event":"app.progress"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String progressId;
    @SuppressWarnings("unused") private String message;
    @SuppressWarnings("unused") private boolean finished;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventProgressMessage(this, controller);
    }
  }

  private static class DeviceAddedEvent extends Event {
    // "event":"device.added"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;
    @SuppressWarnings("unused") private boolean emulator;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDeviceAdded(this, controller);
    }
  }

  private static class DeviceRemovedEvent extends Event {
    // "event":"device.removed"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;
    @SuppressWarnings("unused") private boolean emulator;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDeviceRemoved(this, controller);
    }
  }

  static class AppStartEvent extends Event {
    // "event":"app.start"
    @SuppressWarnings("unused") String appId;
    @SuppressWarnings("unused") String deviceId;
    @SuppressWarnings("unused") String directory;
    @SuppressWarnings("unused") boolean supportsRestart;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      // This event is ignored. The app.start command response is used instead.
    }
  }

  static class AppStartedEvent extends Event {
    // "event":"app.started"
    @SuppressWarnings("unused") String appId;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      RunningFlutterApp app = manager.findApp(controller, appId);
      assert app != null;
      app.changeState(FlutterApp.State.STARTED);
    }
  }

  private static class AppStoppedEvent extends Event {
    // "event":"app.stop"
    @SuppressWarnings("unused") private String appId;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventAppStopped(this, controller);
    }
  }

  private static class AppDebugPortEvent extends Event {
    // "event":"app.eventDebugPort"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private int port;
    @SuppressWarnings("unused") private String baseUri;

    void process(FlutterAppManager manager, FlutterDaemonController controller) {
      manager.eventDebugPort(this, controller);
    }
  }
}

class StopWatch {
  private long startTime;
  private long stopTime;

  StopWatch() {

  }

  public void start() {
    startTime = System.currentTimeMillis();
  }

  public void stop() {
    stopTime = System.currentTimeMillis();
  }

  public long getTime() {
    return stopTime - startTime;
  }
}

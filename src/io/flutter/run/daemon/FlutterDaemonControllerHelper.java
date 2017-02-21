/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.gson.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import gnu.trove.THashMap;
import io.flutter.FlutterInitializer;
import io.flutter.utils.StopWatch;
import io.flutter.utils.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeper of running Flutter apps.
 * <p>
 * TODO(messick) Clean up myResponses as things change
 */
class FlutterDaemonControllerHelper {
  private static final Logger LOG = Logger.getInstance(FlutterDaemonControllerHelper.class.getName());

  private static final String CMD_APP_START = "app.start";
  private static final String CMD_APP_STOP = "app.stop";
  private static final String CMD_APP_RESTART = "app.restart";
  private static final String CMD_SERVICE_EXTENSION = "app.callServiceExtension";
  private static final String CMD_DEVICE_ENABLE = "device.enable";

  private static final Gson GSON = new Gson();

  private final FlutterDaemonController myController;
  private final List<FlutterApp> myApps = new ArrayList<>();
  private final Map<Integer, List<Command>> myPendingCommands = new THashMap<>();
  private int myCommandId = 0;
  private final Object myLock = new Object();
  private final Map<Method, FlutterJsonObject> myResponses = new THashMap<>();
  private ProgressHelper myProgressHandler;
  private StopWatch myProgressStopWatch;

  FlutterDaemonControllerHelper(@NotNull FlutterDaemonController controller) {
    myController = controller;

    myController.addDaemonListener(new DaemonListener() {
      public void daemonInput(String string, FlutterDaemonController controller) {
        processInput(string, controller);
      }

      @Override
      public void aboutToTerminate(ProcessHandler handler, FlutterDaemonController controller) {
        aboutToTerminateAll(controller);
      }

      @Override
      public void processTerminated(ProcessHandler handler, FlutterDaemonController controller) {
        // TODO:
        terminateAllFor(controller);
      }
    });
  }

  @NotNull
  public FlutterApp appStarting(@Nullable String deviceId,
                                @NotNull RunMode mode,
                                @NotNull Project project,
                                boolean startPaused,
                                boolean isHot) {
    myProgressHandler = new ProgressHelper(project);
    final FlutterApp app = new FlutterApp(myController, this, mode, project, isHot);
    synchronized (myLock) {
      myApps.add(app);
    }
    app.changeState(FlutterApp.State.STARTING);
    return app;
  }

  @Nullable
  private FlutterApp waitForApp(@NotNull FlutterDaemonController controller, @NotNull String appId) {
    final long timeout = 10000L;
    final FlutterApp[] resp = {null};
    TimeoutUtil.executeWithTimeout(timeout, () -> {
      while (resp[0] == null) {
        final FlutterApp app = findApp(appId);
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
    return waitForResponse(cmd, timeout);
  }

  @Nullable
  private FlutterJsonObject waitForResponse(@NotNull Method cmd, final long timeout) {
    final FlutterJsonObject[] resp = {null};
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

  void processInput(@NotNull String string, @NotNull FlutterDaemonController controller) {
    try {
      final JsonParser jp = new JsonParser();
      final JsonElement elem = jp.parse(string);
      final JsonObject obj = elem.getAsJsonObject();
      final JsonPrimitive primId = obj.getAsJsonPrimitive("id");
      if (primId == null) {
        handleEvent(obj, controller, string);
      }
      else {
        handleResponse(primId.getAsInt(), obj, controller);
      }
    }
    catch (JsonSyntaxException ex) {
      LOG.error(ex);
    }
  }

  void handleResponse(int cmdId, @NotNull JsonObject obj, @NotNull FlutterDaemonController controller) {
    final Command cmd = findPendingCmd(cmdId, controller);
    try {
      cmd.method.process(obj, this, controller);
    }
    finally {
      removePendingCmd(cmdId, cmd);
    }
  }

  void handleEvent(@NotNull JsonObject obj, @NotNull FlutterDaemonController controller, @NotNull String json) {
    final JsonPrimitive primEvent = obj.getAsJsonPrimitive("event");
    if (primEvent == null) {
      LOG.error("Invalid JSON from flutter: " + json);
      return;
    }
    final String eventName = primEvent.getAsString();
    final JsonObject params = obj.getAsJsonObject("params");
    if (eventName == null || params == null) {
      LOG.error("Bad event from flutter: " + json);
      return;
    }
    final Event eventHandler = eventHandler(eventName, json);
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
      final AppStop appStop = new AppStop(app.appId());
      final Method cmd = makeMethod(CMD_APP_STOP, appStop);
      // This needs to run synchronously. The next thing that happens is the process
      // streams are closed which immediately terminates the process.
      CompletableFuture
        .completedFuture(sendCommand(cmd))
        .thenApply((Method method) -> waitForResponse(method, 300))
        .thenAccept((stopped) -> {
          synchronized (myLock) {
            myApps.remove(app);
          }
        });
    }
  }

  void restartApp(@NotNull FlutterApp app, boolean isFullRestart, boolean pauseAfterRestart) {
    final AppRestart appStart = new AppRestart(app.appId(), isFullRestart, pauseAfterRestart);
    app.changeState(FlutterApp.State.STARTING);
    final Method cmd = makeMethod(CMD_APP_RESTART, appStart);
    sendCommand(cmd);
  }

  void callServiceExtension(FlutterApp app, String name, Map<String, Object> params) {
    sendCommand(makeMethod(CMD_SERVICE_EXTENSION, new AppServiceExtension(app.appId(), name, params)));
  }

  private void appStopped(AppStop stopped, FlutterDaemonController controller) {
    final Stream<Command> starts = findAllPendingCmds(controller).stream().filter(c -> {
      final Params p = c.method.params;
      if (p instanceof AppStop) {
        final AppStop a = (AppStop)p;
        if (a.appId.equals(stopped.appId)) return true;
      }
      return false;
    });
    final Optional<Command> opt = starts.findFirst();
    assert (opt.isPresent());
    final FlutterApp app = findApp(stopped.appId);
    if (app != null) {
      app.changeState(FlutterApp.State.TERMINATED);
    }
    final Command cmd = opt.get();
    synchronized (myLock) {
      myResponses.put(cmd.method, stopped);
    }
  }

  void enableDevicePolling() {
    final Method method = makeMethod(CMD_DEVICE_ENABLE, null);
    sendCommand(method);
  }

  void aboutToTerminateAll(FlutterDaemonController controller) {
    final List<FlutterApp> apps;
    synchronized (myLock) {
      apps = new ArrayList<>(myApps);
    }
    apps.stream().filter(app -> app.getController() == controller).forEach(this::stopApp);
  }

  void terminateAllFor(FlutterDaemonController controller) {
    synchronized (myLock) {
      final ListIterator<FlutterApp> itor = myApps.listIterator();
      while (itor.hasNext()) {
        final FlutterApp app = itor.next();
        if (app.getController() == controller) {
          itor.remove();
        }
      }
    }
  }

  @NotNull
  private Method makeMethod(@NotNull String methodName, @Nullable Params params) {
    return new Method(methodName, params, myCommandId++);
  }

  private Method sendCommand(@NotNull Method method) {
    myController.sendCommand(GSON.toJson(method), this);
    addPendingCmd(method.id, new Command(method, myController));
    return method;
  }

  @NotNull
  private List<Command> findAllPendingCmds(@NotNull FlutterDaemonController controller) {
    final List<Command> result = new ArrayList<>();
    for (List<Command> list : myPendingCommands.values()) {
      result.addAll(list.stream().filter(cmd -> cmd.controller == controller).collect(Collectors.toList()));
    }
    return result;
  }

  @NotNull
  private Command findPendingCmd(int id, @NotNull FlutterDaemonController controller) {
    final List<Command> list = myPendingCommands.get(id);
    for (Command cmd : list) {
      if (cmd.controller == controller) return cmd;
    }
    throw new IllegalStateException("no matching pending command");
  }

  private void removePendingCmd(int id, @NotNull Command command) {
    final List<Command> list = myPendingCommands.get(id);
    list.remove(command);
    if (list.isEmpty()) {
      myPendingCommands.remove(id);
    }
  }

  private void addPendingCmd(int id, @NotNull Command command) {
    final List<Command> list = myPendingCommands.computeIfAbsent(id, k -> new ArrayList<>());
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

  private void eventLogMessage(@NotNull LogMessageEvent message) {
    LOG.info(message.message);
  }

  private void eventLogMessage(@NotNull AppLogEvent message) {
    final FlutterApp app = findApp(message.appId);

    if (app == null) {
      return;
    }

    app.getConsole().print(message.log + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  private void eventProgressMessage(@NotNull AppProgressEvent message) {
    final FlutterApp app = findApp(message.appId);

    if (app == null) {
      return;
    }

    if (message.finished) {
      myProgressHandler.done();

      if (myProgressStopWatch != null) {
        myProgressStopWatch.stop();

        if (message.progressId != null && message.progressId.startsWith("hot.")) {
          if (message.progressId.equals("hot.reload")) {
            app.getConsole().print("\nReloaded in " + myProgressStopWatch.getTimeMillis() + "ms.\n", ConsoleViewContentType.NORMAL_OUTPUT);
            FlutterInitializer.getAnalytics().sendTiming("run", "reload", myProgressStopWatch.getTimeMillis());
          }
          else if (message.progressId.equals("hot.restart")) {
            app.getConsole().print("\nRestarted in " + myProgressStopWatch.getTimeMillis() + "ms.\n", ConsoleViewContentType.NORMAL_OUTPUT);
            FlutterInitializer.getAnalytics().sendTiming("run", "restart", myProgressStopWatch.getTimeMillis());
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
      myController.getService().addConnectedDevice(new FlutterDevice(added.name, added.id, added.platform, added.emulator));
    }
  }

  private void eventDeviceRemoved(@NotNull DeviceRemovedEvent removed, @NotNull FlutterDaemonController controller) {
    synchronized (myLock) {
      myController.getService().getConnectedDevices().stream().filter(
        device -> device.deviceName().equals(removed.name) &&
                  device.deviceId().equals(removed.id) &&
                  device.platform().equals(removed.platform))
        .forEach(myController.getService()::removeConnectedDevice);
    }
  }

  private void eventAppStart(AppStartEvent event, FlutterDaemonController controller) {
    for (FlutterApp app : myApps) {
      if (!app.hasAppId()) {
        app.setAppId(event.appId);
        break;
      }
    }
  }

  private void eventAppStarted(@NotNull AppStartEvent started, @NotNull FlutterDaemonController controller) {
    final Stream<Command> starts = findAllPendingCmds(controller).stream().filter(c -> {
      final Params p = c.method.params;
      if (p instanceof AppStart) {
        final AppStart a = (AppStart)p;
        if (a.deviceId.equals(started.deviceId)) return true;
      }
      return false;
    });
    final Optional<Command> opt = starts.findFirst();
    assert (opt.isPresent());
    final Command cmd = opt.get();
    synchronized (myLock) {
      myResponses.put(cmd.method, started);
    }
  }

  private void eventAppStopped(@NotNull AppStoppedEvent stopped) {
    myProgressHandler.cancel();

    final FlutterApp app = findApp(stopped.appId);
    if (app != null) {
      app.changeState(FlutterApp.State.TERMINATED);
    }
  }

  private void eventDebugPort(@NotNull AppDebugPortEvent port, @NotNull FlutterDaemonController controller) {
    final FlutterApp app = waitForApp(controller, port.appId);
    if (app != null) {
      app.setWsUrl(port.wsUri);

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

  private static void error(JsonObject json) {
    LOG.warn("Flutter process responded with an error: " + json.toString());
  }

  private FlutterApp findApp(String appId) {
    synchronized (myLock) {
      for (FlutterApp app : myApps) {
        if (app.hasAppId() && app.appId().equals(appId)) {
          return app;
        }
      }
    }
    return null;
  }

  private static class Command {
    @NotNull final Method method;
    @NotNull final FlutterDaemonController controller;

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

    @SuppressWarnings("unused") private final String method;
    @SuppressWarnings("unused") private final Params params;
    @SuppressWarnings("unused") private final int id;

    void process(JsonObject obj, FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      if (params != null) params.process(obj, manager, controller);
    }
  }

  private abstract static class Params extends FlutterJsonObject {

    abstract void process(JsonObject obj, FlutterDaemonControllerHelper manager, FlutterDaemonController controller);
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

    @SuppressWarnings("unused") private final String deviceId;
    @SuppressWarnings("unused") private final String projectDirectory;
    @SuppressWarnings("unused") private boolean startPaused = true;
    @SuppressWarnings("unused") private final String route;
    @SuppressWarnings("unused") private final String mode;
    @SuppressWarnings("unused") private final String target;
    @SuppressWarnings("unused") private final boolean hot;

    void process(JsonObject obj, FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      final JsonObject result = obj.getAsJsonObject("result");
      if (result == null) {
        error(obj);
        return;
      }
      final AppStartEvent app = new AppStartEvent();
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

    @SuppressWarnings("unused") private final String appId;
    @SuppressWarnings("unused") private final boolean fullRestart;
    @SuppressWarnings("unused") private final boolean pause;

    void process(JsonObject obj, FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      final FlutterApp app = manager.findApp(appId);
      assert app != null;
      app.changeState(FlutterApp.State.STARTED);
    }
  }

  private static class AppStop extends Params {
    // "method":"app.stop"
    AppStop(String appId) {
      this.appId = appId;
    }

    @SuppressWarnings("unused") private final String appId;

    void process(JsonObject obj, FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
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
          error(obj);
          manager.appStopped(this, controller);
        }
      }
    }
  }

  private static class AppServiceExtension extends Params {
    @SuppressWarnings("unused") private final String appId;
    @SuppressWarnings("unused") private final String methodName;
    @SuppressWarnings("unused") private final Map<String, Object> params;

    AppServiceExtension(String appId, String methodName, Map<String, Object> params) {
      this.appId = appId;
      this.methodName = methodName;
      this.params = params;
    }

    void process(JsonObject obj, FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      if (obj.has("error")) {
        error(obj);
      }
    }
  }

  private abstract static class Event extends FlutterJsonObject {
    Event from(JsonElement element) {
      return GSON.fromJson(element, (Type)this.getClass());
    }

    abstract void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller);
  }

  @SuppressWarnings("CanBeFinal")
  private static class LogMessageEvent extends Event {
    // "event":"daemon.eventLogMessage"
    @SuppressWarnings("unused") private String level;
    @SuppressWarnings("unused") private String message;
    @SuppressWarnings("unused") private String stackTrace;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventLogMessage(this);
    }
  }

  @SuppressWarnings("CanBeFinal")
  private static class AppLogEvent extends Event {
    // "event":"app.log"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String log;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventLogMessage(this);
    }
  }

  @SuppressWarnings("CanBeFinal")
  private static class AppProgressEvent extends Event {
    // "event":"app.progress"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String progressId;
    @SuppressWarnings("unused") private String message;
    @SuppressWarnings("unused") private boolean finished;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventProgressMessage(this);
    }
  }

  @SuppressWarnings("CanBeFinal")
  private static class DeviceAddedEvent extends Event {
    // "event":"device.added"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;
    @SuppressWarnings("unused") private boolean emulator;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventDeviceAdded(this, controller);
    }
  }

  @SuppressWarnings("CanBeFinal")
  private static class DeviceRemovedEvent extends Event {
    // "event":"device.removed"
    @SuppressWarnings("unused") private String id;
    @SuppressWarnings("unused") private String name;
    @SuppressWarnings("unused") private String platform;
    @SuppressWarnings("unused") private boolean emulator;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventDeviceRemoved(this, controller);
    }
  }

  static class AppStartEvent extends Event {
    // "event":"app.start"
    @SuppressWarnings("unused") String appId;
    @SuppressWarnings("unused") String deviceId;
    @SuppressWarnings("unused") String directory;
    @SuppressWarnings("unused") boolean supportsRestart;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventAppStart(this, controller);
    }
  }

  @SuppressWarnings("CanBeFinal")
  static class AppStartedEvent extends Event {
    // "event":"app.started"
    @SuppressWarnings("unused") String appId;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      final FlutterApp app = manager.findApp(appId);
      assert app != null;
      app.changeState(FlutterApp.State.STARTED);
    }
  }

  private static class AppStoppedEvent extends Event {
    // "event":"app.stop"
    @SuppressWarnings({"unused", "CanBeFinal"}) private String appId;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventAppStopped(this);
    }
  }

  @SuppressWarnings("CanBeFinal")
  private static class AppDebugPortEvent extends Event {
    // "event":"app.eventDebugPort"
    @SuppressWarnings("unused") private String appId;
    @SuppressWarnings("unused") private String wsUri;
    @SuppressWarnings("unused") private String baseUri;

    void process(FlutterDaemonControllerHelper manager, FlutterDaemonController controller) {
      manager.eventDebugPort(this, controller);
    }
  }
}

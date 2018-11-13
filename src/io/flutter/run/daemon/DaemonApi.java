/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.base.Charsets;
import com.google.gson.*;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.StdoutJsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sends JSON commands to a flutter daemon process, assigning a new id to each one.
 *
 * <p>Also handles dispatching incoming responses and events.
 *
 * <p>The protocol is specified in
 * <a href="https://github.com/flutter/flutter/wiki/The-flutter-daemon-mode"
 * >The Flutter Daemon Mode</a>.
 */
public class DaemonApi {
  private static final int STDERR_LINES_TO_KEEP = 100;

  @NotNull private final Consumer<String> callback;
  private final AtomicInteger nextId = new AtomicInteger();
  private final Map<Integer, Command> pending = new LinkedHashMap<>();

  private final StdoutJsonParser stdoutParser = new StdoutJsonParser();

  /**
   * A ring buffer holding the last few lines that the process sent to stderr.
   */
  private final Deque<String> stderr = new ArrayDeque<>();

  /**
   * Creates an Api that sends JSON to a callback.
   */
  DaemonApi(@NotNull Consumer<String> callback) {
    this.callback = callback;
  }

  /**
   * Creates an Api that sends JSON to a process.
   */
  DaemonApi(@NotNull ProcessHandler process) {
    this((String json) -> sendCommand(json, process));
  }

  // app domain

  CompletableFuture<RestartResult> restartApp(@NotNull String appId, boolean fullRestart, boolean pause, @NotNull String reason) {
    return send("app.restart", new AppRestart(appId, fullRestart, pause, reason));
  }

  CompletableFuture<Boolean> stopApp(@NotNull String appId) {
    return send("app.stop", new AppStop(appId));
  }

  void cancelPending() {
    final List<Command> commands;

    synchronized (pending) {
      commands = new ArrayList<>(pending.values());
      pending.clear();
    }

    for (Command command : commands) {
      command.completeExceptionally(new IOException("Application terminated"));
    }
  }

  /**
   * Used to invoke an arbitrary service protocol extension.
   */
  CompletableFuture<JsonObject> callAppServiceExtension(@NotNull String appId,
                                                        @NotNull String methodName,
                                                        @NotNull Map<String, Object> params) {
    return send("app.callServiceExtension", new AppServiceExtension(appId, methodName, params));
  }

  // device domain

  CompletableFuture enableDeviceEvents() {
    return send("device.enable", null);
  }

  /**
   * Receive responses and events from a process until it shuts down.
   */
  void listen(@NotNull ProcessHandler process, @NotNull DaemonEvent.Listener listener) {
    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputType.equals(ProcessOutputTypes.STDERR)) {
          // Append text to last line in buffer.
          final String last = stderr.peekLast();
          if (last != null && !last.endsWith("\n")) {
            stderr.removeLast();
            stderr.add(last + event.getText());
          }
          else {
            stderr.add(event.getText());
          }

          // Trim buffer size.
          while (stderr.size() > STDERR_LINES_TO_KEEP) {
            stderr.removeFirst();
          }
        }
        else if (outputType.equals(ProcessOutputTypes.STDOUT)) {
          final String text = event.getText();

          if (FlutterSettings.getInstance().isVerboseLogging()) {
            LOG.info("[<-- " + text.trim() + "]");
          }

          stdoutParser.appendOutput(text);

          for (String line : stdoutParser.getAvailableLines()) {
            final JsonObject obj = parseAndValidateDaemonEvent(line);
            if (obj != null) {
              dispatch(obj, listener);
            }
          }
        }
      }

      @Override
      public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
        listener.processWillTerminate();
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        listener.processTerminated(event.getExitCode());
      }
    });

    // All hooked up and ready to receive events.
    process.startNotify();
  }

  /**
   * Parses some JSON and handles it as either a command's response or an event.
   */
  void dispatch(@NotNull JsonObject obj, @Nullable DaemonEvent.Listener eventListener) {
    final JsonPrimitive idField = obj.getAsJsonPrimitive("id");
    if (idField == null) {
      // It's an event.
      if (eventListener != null) {
        DaemonEvent.dispatch(obj, eventListener);
      }
    }
    else {
      final Command cmd = takePending(idField.getAsInt());
      if (cmd == null) {
        return;
      }

      final JsonElement error = obj.get("error");
      if (error != null) {
        cmd.completeExceptionally(new IOException("error from " + cmd.method + ": " + error));
      }
      else {
        cmd.complete(obj.get("result"));
      }
    }
  }

  @Nullable
  private Command takePending(int id) {
    final Command cmd;
    synchronized (pending) {
      cmd = pending.remove(id);
    }
    if (cmd == null) {
      LOG.warn("received a response for a request that wasn't sent: " + id);
      return null;
    }
    return cmd;
  }

  private <T> CompletableFuture<T> send(String method, @Nullable Params<T> params) {
    // Synchronize on nextId to ensure that we send one command at a time and they are numbered in the order they are sent.
    synchronized (nextId) {
      final int id = nextId.getAndIncrement();
      final Command<T> command = new Command<>(method, params, id);
      final String json = command.toString();
      synchronized (pending) {
        pending.put(id, command);
      }
      callback.accept(json);
      return command.done;
    }
  }

  /**
   * Parse the given string; if it is valid JSON - and a valid Daemon message - then return
   * the parsed JsonObject.
   */
  public static JsonObject parseAndValidateDaemonEvent(String message) {
    if (!message.startsWith("[{")) {
      return null;
    }

    message = message.trim();
    if (!message.endsWith("}]")) {
      return null;
    }

    message = message.substring(1, message.length() - 1);

    final JsonObject obj;

    try {
      final JsonParser jsonParser = new JsonParser();
      final JsonElement element = jsonParser.parse(message);
      obj = element.getAsJsonObject();
    }
    catch (JsonSyntaxException e) {
      return null;
    }

    // obj must contain either an "id" (int), or an "event" field
    final JsonPrimitive eventField = obj.getAsJsonPrimitive("event");
    if (eventField != null) {
      final String eventName = eventField.getAsString();
      if (eventName == null) {
        return null;
      }
      final JsonObject params = obj.getAsJsonObject("params");
      return params == null ? null : obj;
    }
    else {
      // id
      final JsonPrimitive idField = obj.getAsJsonPrimitive("id");
      if (idField == null || !idField.isNumber()) {
        return null;
      }

      try {
        final int id = idField.getAsInt();
        return obj;
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
  }

  private static void sendCommand(String json, ProcessHandler handler) {
    final PrintWriter stdin = getStdin(handler);
    if (stdin == null) {
      LOG.warn("can't write command to Flutter process: " + json);
      return;
    }
    stdin.write('[');
    stdin.write(json);
    stdin.write("]\n");

    if (FlutterSettings.getInstance().isVerboseLogging()) {
      LOG.info("[--> " + json + "]");
    }

    if (stdin.checkError()) {
      LOG.warn("can't write command to Flutter process: " + json);
    }
  }

  @Nullable
  private static PrintWriter getStdin(ProcessHandler processHandler) {
    final OutputStream stdin = processHandler.getProcessInput();
    if (stdin == null) return null;
    return new PrintWriter(new OutputStreamWriter(stdin, Charsets.UTF_8));
  }

  /**
   * Returns the last lines written to stderr.
   */
  public String getStderrTail() {
    final String[] lines = stderr.toArray(new String[]{});
    return String.join("", lines);
  }

  public static class RestartResult {
    private int code;
    private String message;
    private String hintMessage;
    private String hintId;

    public boolean ok() {
      return code == 0;
    }

    public int getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }

    public String getHintMessage() {
      return hintMessage;
    }

    public String getHintId() {
      return hintId;
    }

    public boolean isRestartRecommended() {
      return "restartRecommended".equals(hintId);
    }

    @Override
    public String toString() {
      return getCode() + ":" + getMessage();
    }
  }

  /**
   * A pending command to a Flutter process.
   */
  private static class Command<T> {
    final @NotNull String method;
    final @Nullable JsonElement params;
    final int id;

    transient final @Nullable Function<JsonElement, T> parseResult;
    transient final CompletableFuture<T> done = new CompletableFuture<>();

    Command(@NotNull String method, @Nullable Params<T> params, int id) {
      this.method = method;
      // GSON has trouble with params as a field, because it has both a generic type and subclasses.
      // But it handles it okay at top-level.
      this.params = GSON.toJsonTree(params);
      this.id = id;
      this.parseResult = params == null ? null : params::parseResult;
    }

    void complete(@Nullable JsonElement result) {
      if (parseResult == null) {
        done.complete(null);
        return;
      }
      try {
        done.complete(parseResult.apply(result));
      }
      catch (Exception e) {
        LOG.warn("Unable to parse response from Flutter daemon. Command was: " + this, e);
        done.completeExceptionally(e);
      }
    }

    void completeExceptionally(Throwable t) {
      done.completeExceptionally(t);
    }

    @Override
    public String toString() {
      return GSON.toJson(this);
    }
  }

  private abstract static class Params<T> {
    @Nullable
    abstract T parseResult(@Nullable JsonElement result);
  }

  @SuppressWarnings("unused")
  private static class AppRestart extends Params<RestartResult> {
    @NotNull final String appId;
    final boolean fullRestart;
    final boolean pause;
    @NotNull final String reason;

    AppRestart(@NotNull String appId, boolean fullRestart, boolean pause, @NotNull String reason) {
      this.appId = appId;
      this.fullRestart = fullRestart;
      this.pause = pause;
      this.reason = reason;
    }

    @Override
    RestartResult parseResult(JsonElement result) {
      return GSON.fromJson(result, RestartResult.class);
    }
  }

  @SuppressWarnings("unused")
  private static class AppStop extends Params<Boolean> {
    @NotNull final String appId;

    AppStop(@NotNull String appId) {
      this.appId = appId;
    }

    @Override
    Boolean parseResult(JsonElement result) {
      return GSON.fromJson(result, Boolean.class);
    }
  }

  @SuppressWarnings("unused")
  private static class AppServiceExtension extends Params<JsonObject> {
    final String appId;
    final String methodName;
    final Map<String, Object> params;

    AppServiceExtension(String appId, String methodName, Map<String, Object> params) {
      this.appId = appId;
      this.methodName = methodName;
      this.params = params;
    }

    @Override
    JsonObject parseResult(JsonElement result) {
      if (result instanceof JsonObject) {
        return (JsonObject)result;
      }

      final JsonObject obj = new JsonObject();
      obj.add("result", result);
      return obj;
    }
  }

  private static final Gson GSON = new Gson();
  private static final Logger LOG = Logger.getInstance(DaemonApi.class);
}

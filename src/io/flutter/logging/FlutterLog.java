/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.EventDispatcher;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.server.vmService.ServiceExtensions;
import io.flutter.server.vmService.VmServiceConsumers;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class FlutterLog implements FlutterLogEntry.ContentListener {

  private static final Logger LOG = Logger.getInstance(FlutterLog.class);

  public static final String LOGGING_STREAM_ID = "_Logging";

  public interface Listener extends EventListener {
    void onEvent(@NotNull FlutterLogEntry entry);

    void onEntryContentChange();
  }

  // derived from: https://github.com/dart-lang/logging
  public enum Level {
    NONE(0),
    FINEST(300),
    FINER(400),
    FINE(500),
    CONFIG(700),
    INFO(800),
    WARNING(900),
    SEVERE(1000),
    SHOUT(1200);

    final int value;

    Level(int value) {
      this.value = value;
    }

    @NotNull
    public static Level forValue(int value) {
      final Level[] levels = Level.values();

      for (int i = levels.length - 1; i >= 0; i--) {
        if (value >= levels[i].value) {
          return levels[i];
        }
      }

      return NONE;
    }

    public String toDisplayString() {
      return name().toLowerCase();
    }
  }

  private final EventDispatcher<Listener>
    dispatcher = EventDispatcher.create(Listener.class);

  private final FlutterLogEntryParser logEntryParser;

  // TODO(pq): consider limiting size.
  private final List<FlutterLogEntry> entries = new ArrayList<>();
  private FlutterApp app;

  public static boolean isLoggingEnabled() {
    return FlutterSettings.getInstance().useFlutterLogView();
  }

  public FlutterLog(@NotNull Project project, @Nullable Module module) {
    logEntryParser = new FlutterLogEntryParser(project, module);
    logEntryParser.addListener(this, project);
  }

  public void addConsoleEntry(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    onEntry(logEntryParser.parseConsoleEvent(text, contentType));
  }

  public void addListener(@NotNull Listener listener, @NotNull Disposable parent) {
    dispatcher.addListener(listener, parent);
  }

  public void clear() {
    // TODO(pq): add locking.
    entries.clear();
  }

  private FlutterDebugProcess getDebugProcess() {
    return logEntryParser.getDebugProcess();
  }

  public CompletableFuture<List<LoggingChannel>> getLoggingChannels() {
    final FlutterDebugProcess debugProcess = getDebugProcess();
    if (debugProcess != null) {
      return debugProcess.getApp().callServiceExtension(ServiceExtensions.loggingChannels).thenApply((response) -> {
        final JsonObject value = response.getAsJsonObject("value");
        final List<LoggingChannel> channels = new ArrayList<>();
        for (String channel : value.keySet()) {
          channels.add(LoggingChannel.fromJson(channel, value.getAsJsonObject(channel)));
        }
        return channels;
      }).exceptionally(e -> {
        FlutterUtils.warn(LOG, e);
        return Collections.emptyList();
      });
    }
    return CompletableFuture.completedFuture(Collections.emptyList());
  }

  public void enable(@NotNull LoggingChannel channel, boolean subscribe) {
    final FlutterDebugProcess debugProcess = getDebugProcess();
    if (debugProcess != null) {
      final Map<String, Object> params = new HashMap<>();
      params.put("channel", channel.name);
      params.put("enabled", subscribe);
      debugProcess.getApp().callServiceExtension(ServiceExtensions.enableLogs, params);
    }
  }

  public List<FlutterLogEntry> getEntries() {
    return ImmutableList.copyOf(entries);
  }

  public void removeListener(@NotNull Listener listener) {
    dispatcher.removeListener(listener);
  }

  private void onEntry(@Nullable FlutterLogEntry entry) {
    if (entry != null) {
      entries.add(entry);
      entry.setSequenceNumber(entries.size());
      dispatcher.getMulticaster().onEvent(entry);
    }
  }

  // TODO(pq): consider inverting and having services do their own listening, and just push entries.
  public void listenToProcess(@NotNull ProcessHandler processHandler, @NotNull Disposable parent) {
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        onEntry(logEntryParser.parseDaemonEvent(event, outputType));
      }
    }, parent);
  }

  public void listenToVm(@NotNull VmService vmService) {
    // No-op if disabled.
    if (!isLoggingEnabled()) return;

    logEntryParser.setVmServices(app, vmService);

    // TODO(pq): consider moving into VMServiceManager to consolidate vm service listeners.
    vmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        onVmServiceReceived(streamId, event);
      }

      @Override
      public void connectionClosed() {
        onVmConnectionClosed();
      }
    });

    vmService.streamListen(LOGGING_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    vmService.streamListen(VmService.GC_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    // TODO(pq): listen for frame events (Flutter.FrameworkInitialization, Flutter.FirstFrame, Flutter.Frame, etc).
  }

  private void onVmServiceReceived(String id, Event event) {
    final List<FlutterLogEntry> entries = logEntryParser.parse(id, event);
    if (entries != null) {
      entries.forEach(this::onEntry);
    }
  }

  @SuppressWarnings("EmptyMethod")
  private void onVmConnectionClosed() {
    // TODO(pq): handle VM connection closed.
  }

  public void setFlutterApp(FlutterApp app) {
    this.app = app;
  }

  @Override
  public void onContentUpdate() {
    dispatcher.getMulticaster().onEntryContentChange();
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import com.intellij.util.EventDispatcher;
import io.flutter.server.vmService.VmServiceConsumers;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

public class FlutterLog {
  public static final String LOGGING_STREAM_ID = "_Logging";

  public interface Listener extends EventListener {
    void onEvent(@NotNull FlutterLogEntry entry);
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

    public static Level forValue(int value) {
      final Level[] levels = Level.values();
      for (int i = 0; i < levels.length; ++i) {
        if (value >= levels[i].value && ((i >= levels.length - 1) || value < levels[i + 1].value)) {
          return levels[i];
        }
      }
      return null;
    }
  }
  private final EventDispatcher<Listener>
    dispatcher = EventDispatcher.create(Listener.class);

  private final FlutterLogEntryParser logEntryParser = new FlutterLogEntryParser();

  // TODO(pq): consider limiting size.
  private final List<FlutterLogEntry> entries = new ArrayList<>();

  public static boolean isLoggingEnabled() {
    return FlutterSettings.getInstance().useFlutterLogView();
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

    // TODO(pq): consider moving into PerfService to consolidate vm service listeners.
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

    // Listen for logging events (note: no way to unregister).
    vmService.streamListen(LOGGING_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    // TODO(pq): listen for GC and frame events (Flutter.FrameworkInitialization, Flutter.FirstFrame, Flutter.Frame, etc).
  }

  private void onVmServiceReceived(String id, Event event) {
    onEntry(logEntryParser.parse(id, event));
  }

  @SuppressWarnings("EmptyMethod")
  private void onVmConnectionClosed() {
    // TODO(pq): handle VM connection closed.
  }
}

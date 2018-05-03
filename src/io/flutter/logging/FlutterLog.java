/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FlutterLog {

  // (Temporary) flag to enable logging support.
  public static final boolean LOGGING_ENABLED = false;
  public static final String LOGGING_STREAM_ID = "_Logging";

  private final List<Listener> listeners = new ArrayList<>();

  public void addListener(@NotNull Listener listener, @Nullable Disposable parent) {
    listeners.add(listener);
    if (parent != null) {
      Disposer.register(parent, () -> listeners.remove(listener));
    }
  }

  public void removeListener(@NotNull Listener listener) {
    listeners.remove(listener);
  }

  private void onEntry(@Nullable FlutterLogEntry entry) {
    if (entry != null) {
      for (Listener listener : listeners) {
        listener.onEvent(entry);
      }
    }
  }

  // TODO(pq): consider inverting and having services do their own listening, and just push entries.
  public void listenToProcess(@NotNull ProcessHandler processHandler, @NotNull Disposable parent) {
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        onEntry(FlutterLogEntryParser.parseDaemonEvent(event, outputType));
      }
    }, parent);
  }

  public void listenToVm(@NotNull VmService vmService) {
    // No-op if disabled.
    if (!LOGGING_ENABLED) return;

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
    onEntry(FlutterLogEntryParser.parse(id, event));
  }

  @SuppressWarnings("EmptyMethod")
  private void onVmConnectionClosed() {
    // TODO(pq): handle VM connection closed.
  }

  public interface Listener {
    void onEvent(@NotNull FlutterLogEntry entry);
  }
}

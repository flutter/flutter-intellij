/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import io.flutter.perf.HeapMonitor.HeapListener;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.IsolateRef;
import org.jetbrains.annotations.NotNull;

// TODO(pq): rename
// TODO(pq): improve error handling
// TODO(pq): change mode for opting in (preference or inspector view menu)

public class PerfService {
  @NotNull private final HeapMonitor heapMonitor;
  @NotNull private final FlutterFramesMonitor flutterFramesMonitor;

  private boolean isRunning;

  public PerfService(@NotNull FlutterDebugProcess debugProcess, @NotNull VmService vmService) {
    this.heapMonitor = new HeapMonitor(vmService, debugProcess);
    this.flutterFramesMonitor = new FlutterFramesMonitor(vmService);

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
    vmService.streamListen("GC", VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
  }

  /**
   * Start the Perf service.
   */
  public void start() {
    if (!isRunning) {
      // Start polling.
      heapMonitor.start();
      isRunning = true;
    }
  }

  /**
   * Stop the Perf service.
   */
  public void stop() {
    if (isRunning) {
      // The vmService does not have a way to remove listeners, so we can only stop paying attention.
      heapMonitor.stop();
    }
  }

  private void onVmConnectionClosed() {
    if (isRunning) {
      heapMonitor.stop();
    }

    isRunning = false;
  }

  @SuppressWarnings("EmptyMethod")
  private void onVmServiceReceived(String streamId, Event event) {
    if (!isRunning) {
      return;
    }

    if (StringUtil.equals(streamId, VmService.GC_STREAM_ID)) {
      final IsolateRef isolateRef = event.getIsolate();
      final HeapMonitor.HeapSpace newHeapSpace = new HeapMonitor.HeapSpace(event.getJson().getAsJsonObject("new"));
      final HeapMonitor.HeapSpace oldHeapSpace = new HeapMonitor.HeapSpace(event.getJson().getAsJsonObject("old"));

      heapMonitor.handleGCEvent(isolateRef, newHeapSpace, oldHeapSpace);
    }
  }

  @NotNull
  public FlutterFramesMonitor getFlutterFramesMonitor() {
    return flutterFramesMonitor;
  }

  /**
   * Add a listener for heap state updates.
   */
  public void addListener(@NotNull HeapListener listener) {
    final boolean hadListeners = heapMonitor.hasListeners();

    heapMonitor.addListener(listener);

    if (!hadListeners) {
      start();
    }
  }

  /**
   * Remove a heap listener.
   */
  public void removeListener(@NotNull HeapListener listener) {
    heapMonitor.removeListener(listener);

    if (!heapMonitor.hasListeners()) {
      stop();
    }
  }
}

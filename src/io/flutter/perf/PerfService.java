/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import io.flutter.perf.HeapMonitor.HeapListener;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.VmServiceListener;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;

// TODO(pq): rename
// TODO(pq): improve error handling
// TODO(pq): change mode for opting in (preference or inspector view menu)
public class PerfService {

  // Enable to see experimental heap status panel in the inspector view.
  public static final boolean DISPLAY_HEAP_USE = false;

  private final VmServiceListener vmServiceListener = new VmServiceListenerAdapter() {
    @Override
    public void received(String streamId, Event event) {
      onVmServiceReceived(streamId, event);
    }
    @Override
    public void connectionClosed() {
      onVmConnectionClosed();
    }
  };

  @NotNull
  private final HeapMonitor heapMonitor;
  @NotNull
  private final VmService vmService;

  private boolean isRunning;

  public PerfService(@NotNull FlutterDebugProcess debugProcess, @NotNull VmService vmService) {
    this.heapMonitor = new HeapMonitor(vmService, debugProcess);
    this.vmService = vmService;
  }

  /**
   * Start the Perf service.
   */
  public void start() {
    if (!isRunning) {
      // Start polling.
      vmService.addVmServiceListener(vmServiceListener);
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
  }

  @SuppressWarnings("EmptyMethod")
  private void onVmServiceReceived(String id, Event event) {
    // TODO(pq): handle receive -- errors in particular.
  }

  /**
   * Add a listener for heap state updates.
   */
  public void addListener(@NotNull HeapListener listener) {
    heapMonitor.addListener(listener);
  }

  /**
   * Remove a heap listener.
   */
  public void removeListener(@NotNull HeapListener listener) {
    heapMonitor.removeListener(listener);
  }
}

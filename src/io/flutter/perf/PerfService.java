/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import gnu.trove.THashSet;
import io.flutter.perf.HeapMonitor.HeapListener;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

// TODO(pq): rename
// TODO(pq): improve error handling
// TODO(pq): change mode for opting in (preference or inspector view menu)

public class PerfService {
  @NotNull private final HeapMonitor heapMonitor;
  @NotNull private final FlutterFramesMonitor flutterFramesMonitor;
  @NotNull private final Set<String> serviceExtensions = new THashSet<>();

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

    vmService.streamListen(VmService.GC_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    vmService.streamListen(VmService.EXTENSION_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    vmService.streamListen(VmService.ISOLATE_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    // Populate the service extensions info.
    vmService.getVM(new VMConsumer() {
      @Override
      public void received(VM vm) {
        for (IsolateRef ref : vm.getIsolates()) {
          vmService.getIsolate(ref.getId(), new GetIsolateConsumer() {
            @Override
            public void onError(RPCError error) {
            }

            @Override
            public void received(Isolate isolate) {
              serviceExtensions.addAll(isolate.getExtensionRPCs());
            }

            @Override
            public void received(Sentinel sentinel) {
            }
          });
        }
      }

      @Override
      public void onError(RPCError error) {
      }
    });
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
    else if (StringUtil.equals(streamId, VmService.ISOLATE_STREAM_ID) && event.getKind() == EventKind.ServiceExtensionAdded) {
      serviceExtensions.add(event.getExtensionRPC());
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

  public boolean hasServiceExtension(String name) {
    return serviceExtensions.contains(name);
  }
}

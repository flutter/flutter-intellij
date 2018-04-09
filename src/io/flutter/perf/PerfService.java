/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
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
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.Set;

// TODO(pq): rename
// TODO(pq): improve error handling
// TODO(pq): change mode for opting in (preference or inspector view menu)

public class PerfService {
  public interface FlutterIsolateListener extends EventListener {
    void handleFutterIsolateChanged(@Nullable IsolateRef isolateRef);
  }

  @NotNull private final HeapMonitor heapMonitor;
  @NotNull private final FlutterFramesMonitor flutterFramesMonitor;
  @NotNull private final Set<String> serviceExtensions = new THashSet<>();

  private final EventDispatcher<FlutterIsolateListener> myIsolateEventDispatcher = EventDispatcher.create(FlutterIsolateListener.class);

  private IsolateRef flutterIsolateRef;
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

    vmService.streamListen(VmService.ISOLATE_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    vmService.streamListen(VmService.EXTENSION_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    vmService.streamListen(VmService.GC_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    // Populate the service extensions info and look for any Flutter views.
    // TODO(devoncarew): This currently returns the first Flutter view found as the
    // current Flutter isolate, and ignores any other Flutter views running in the app.
    // In the future, we could add more first class support for multiple Flutter views.
    vmService.getVM(new VMConsumer() {
      @Override
      public void received(VM vm) {
        for (final IsolateRef isolateRef : vm.getIsolates()) {
          vmService.getIsolate(isolateRef.getId(), new GetIsolateConsumer() {
            @Override
            public void onError(RPCError error) {
            }

            @Override
            public void received(Isolate isolate) {
              // Populate flutter isolate info.
              if (flutterIsolateRef == null) {
                for (String extensionName : isolate.getExtensionRPCs()) {
                  if (extensionName.startsWith("ext.flutter.")) {
                    flutterIsolateRef = isolateRef;
                    myIsolateEventDispatcher.getMulticaster().handleFutterIsolateChanged(flutterIsolateRef);
                    break;
                  }
                }
              }

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
   * Return the current Flutter isolate.
   * <p>
   * This can be null occasionally during initial application startup and for a brief time when doing a full restart.
   */
  @Nullable
  public IsolateRef getCurrentFlutterIsolate() {
    return flutterIsolateRef;
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
    // Check for the current Flutter isolate exiting.
    if (flutterIsolateRef != null) {
      if (event.getKind() == EventKind.IsolateExit && StringUtil.equals(event.getIsolate().getId(), flutterIsolateRef.getId())) {
        flutterIsolateRef = null;
        myIsolateEventDispatcher.getMulticaster().handleFutterIsolateChanged(flutterIsolateRef);
      }
    }

    // Check to see if there's a new Flutter isolate.
    if (flutterIsolateRef == null) {
      // Check for Flutter frame events.
      if (event.getKind() == EventKind.Extension && event.getExtensionKind().startsWith("Flutter.")) {
        // Flutter.FrameworkInitialization, Flutter.FirstFrame, Flutter.Frame
        flutterIsolateRef = event.getIsolate();
        myIsolateEventDispatcher.getMulticaster().handleFutterIsolateChanged(flutterIsolateRef);
      }

      // Check for service extension registrations.
      if (event.getKind() == EventKind.ServiceExtensionAdded) {
        final String extensionName = event.getExtensionRPC();

        if (extensionName.startsWith("ext.flutter.")) {
          flutterIsolateRef = event.getIsolate();
          myIsolateEventDispatcher.getMulticaster().handleFutterIsolateChanged(flutterIsolateRef);
        }
      }
    }

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
  public void addHeapListener(@NotNull HeapListener listener) {
    final boolean hadListeners = heapMonitor.hasListeners();

    heapMonitor.addListener(listener);

    if (!hadListeners) {
      start();
    }
  }

  /**
   * Remove a heap listener.
   */
  public void removeHeapListener(@NotNull HeapListener listener) {
    heapMonitor.removeListener(listener);

    if (!heapMonitor.hasListeners()) {
      stop();
    }
  }

  public void addFlutterIsolateListener(@NotNull FlutterIsolateListener listener) {
    myIsolateEventDispatcher.addListener(listener);
  }

  public void removeFlutterIsolateListener(@NotNull FlutterIsolateListener listener) {
    myIsolateEventDispatcher.removeListener(listener);
  }

  public boolean hasServiceExtension(String name) {
    return serviceExtensions.contains(name);
  }
}

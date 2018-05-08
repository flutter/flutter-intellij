/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import gnu.trove.THashMap;
import io.flutter.perf.HeapMonitor.HeapListener;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

// TODO(pq/devoncarew): Find a better name for this class; VMServiceWrapper? VMServiceManager?

public class PerfService {
  @NotNull private final HeapMonitor heapMonitor;
  @NotNull private final FlutterFramesMonitor flutterFramesMonitor;
  @NotNull private final Map<String, EventStream<Boolean>> serviceExtensions = new THashMap<>();

  private final EventStream<IsolateRef> flutterIsolateRefStream;

  private boolean isRunning;

  public PerfService(@NotNull FlutterDebugProcess debugProcess, @NotNull VmService vmService) {
    this.heapMonitor = new HeapMonitor(vmService, debugProcess);
    this.flutterFramesMonitor = new FlutterFramesMonitor(vmService);
    flutterIsolateRefStream = new EventStream<>();

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
              if (flutterIsolateRefStream.getValue() == null) {
                for (String extensionName : isolate.getExtensionRPCs()) {
                  if (extensionName.startsWith("ext.flutter.")) {
                    setFlutterIsolate(isolateRef);
                    break;
                  }
                }
              }

              ApplicationManager.getApplication().invokeLater(() -> {
                for (String extension : isolate.getExtensionRPCs()) {
                  addServiceExtension(extension);
                }
              });
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
   * Returns a StreamSubscription providing the current Flutter isolate.
   * <p>
   * The current value of the subscription can be null occasionally during initial application startup and for a brief time when doing a
   * hot restart.
   */
  public StreamSubscription<IsolateRef> getCurrentFlutterIsolate(Consumer<IsolateRef> onValue, boolean onUIThread) {
    return flutterIsolateRefStream.listen(onValue, onUIThread);
  }

  /**
   * Return the current Flutter IsolateRef, if any.
   * <p>
   * Note that this may not be immediately populated at app startup for Flutter apps; clients that wish to
   * be notified when the Flutter isolate is discovered should prefer the StreamSubscription varient of this
   * method (getCurrentFlutterIsolate()).
   */
  public IsolateRef getCurrentFlutterIsolateRaw() {
    synchronized (flutterIsolateRefStream) {
      return flutterIsolateRefStream.getValue();
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

  private void setFlutterIsolate(IsolateRef ref) {
    synchronized (flutterIsolateRefStream) {
      final IsolateRef existing = flutterIsolateRefStream.getValue();
      if (existing == ref || (existing != null && ref != null && StringUtil.equals(existing.getId(), ref.getId()))) {
        // Isolate didn't change.
        return;
      }
      flutterIsolateRefStream.setValue(ref);
    }
  }

  @SuppressWarnings("EmptyMethod")
  private void onVmServiceReceived(String streamId, Event event) {
    // Check for the current Flutter isolate exiting.
    final IsolateRef flutterIsolateRef = flutterIsolateRefStream.getValue();
    if (flutterIsolateRef != null) {
      if (event.getKind() == EventKind.IsolateExit && StringUtil.equals(event.getIsolate().getId(), flutterIsolateRef.getId())) {
        setFlutterIsolate(null);

        final Iterable<EventStream<Boolean>> existingExtensions;
        synchronized (serviceExtensions) {
          existingExtensions = new ArrayList<>(serviceExtensions.values());
        }
        for (EventStream<Boolean> serviceExtension : existingExtensions) {
          // The next Flutter isolate to load might not support this service
          // extension.
          serviceExtension.setValue(false);
        }
      }
    }

    if (event.getKind() == EventKind.ServiceExtensionAdded) {
      addServiceExtension(event.getExtensionRPC());
    }

    // Check to see if there's a new Flutter isolate.
    if (flutterIsolateRefStream.getValue() == null) {
      // Check for Flutter frame events.
      if (event.getKind() == EventKind.Extension && event.getExtensionKind().startsWith("Flutter.")) {
        // Flutter.FrameworkInitialization, Flutter.FirstFrame, Flutter.Frame
        setFlutterIsolate(event.getIsolate());
      }

      // Check for service extension registrations.
      if (event.getKind() == EventKind.ServiceExtensionAdded) {
        final String extensionName = event.getExtensionRPC();

        if (extensionName.startsWith("ext.flutter.")) {
          setFlutterIsolate(event.getIsolate());
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
  }

  /**
   * This method must only be called on the UI thread.
   */
  private void addServiceExtension(String name) {
    synchronized (serviceExtensions) {
      final EventStream<Boolean> stream = serviceExtensions.get(name);
      if (stream == null) {
        serviceExtensions.put(name, new EventStream<>(true));
      }
      else if (!stream.getValue()) {
        stream.setValue(true);
      }
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

  public @NotNull
  StreamSubscription<Boolean> hasServiceExtension(String name, Consumer<Boolean> onData) {
    EventStream<Boolean> stream;
    synchronized (serviceExtensions) {
      stream = serviceExtensions.get(name);
      if (stream == null) {
        stream = new EventStream<>(false);
        serviceExtensions.put(name, stream);
      }
    }
    return stream.listen(onData, true);
  }

  public void pausePolling() {
    if (isRunning) {
      heapMonitor.pausePolling();
    }
  }

  public void resumePolling() {
    if (isRunning) {
      heapMonitor.resumePolling();
    }
  }
}

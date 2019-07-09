/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import io.flutter.inspector.EvalOnDartLibrary;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import io.flutter.vmService.HeapMonitor.HeapListener;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class VMServiceManager implements FlutterApp.FlutterAppListener {
  // TODO(devoncarew): Remove on or after approx. Oct 1 2019.
  public static final String LOGGING_STREAM_ID_OLD = "_Logging";

  @NotNull private final FlutterApp app;
  @NotNull private final HeapMonitor heapMonitor;
  @NotNull private final FlutterFramesMonitor flutterFramesMonitor;
  @NotNull private final Map<String, EventStream<Boolean>> serviceExtensions = new THashMap<>();

  /**
   * Boolean value applicable only for boolean service extensions indicating
   * whether the service extension is enabled or disabled.
   */
  @NotNull private final Map<String, EventStream<ServiceExtensionState>> serviceExtensionState = new THashMap<>();

  private final EventStream<IsolateRef> flutterIsolateRefStream;

  private boolean isRunning;
  private int polledCount;

  private volatile boolean firstFrameEventReceived = false;
  private final VmService vmService;
  /**
   * Temporarily stores service extensions that we need to add. We should not add extensions until the first frame event
   * has been received [firstFrameEventReceived].
   */
  private final List<String> pendingServiceExtensions = new ArrayList<>();

  public VMServiceManager(@NotNull FlutterApp app, @NotNull VmService vmService) {
    this.app = app;
    this.vmService = vmService;
    app.addStateListener(this);

    this.heapMonitor = new HeapMonitor(vmService, app.getFlutterDebugProcess());
    this.flutterFramesMonitor = new FlutterFramesMonitor(vmService);
    this.polledCount = 0;
    flutterIsolateRefStream = new EventStream<>();

    // The VM Service depends on events from the Extension event stream to determine when Flutter.Frame
    // events are fired. Without the call to listen, events from the stream will not be sent.
    vmService.streamListen(VmService.EXTENSION_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    vmService.streamListen(LOGGING_STREAM_ID_OLD, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    vmService.streamListen(VmService.LOGGING_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    vmService.streamListen(VmService.GC_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

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
                if (isolate.getExtensionRPCs() != null) {
                  for (String extensionName : isolate.getExtensionRPCs()) {
                    if (extensionName.startsWith(ServiceExtensions.flutterPrefix)) {
                      setFlutterIsolate(isolateRef);
                      break;
                    }
                  }
                }
              }
              addRegisteredExtensionRPCs(isolate, false);
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

  public void addRegisteredExtensionRPCs(Isolate isolate, boolean attach) {
    // If attach was called, there is a risk we may never receive a
    // Flutter.Frame or Flutter.FirstFrame event so we need to query the
    // framework to determine if a frame has already been rendered.
    // This check would be safe to do outside of attach mode but is not needed.
    if (attach && isolate.getExtensionRPCs() != null && !firstFrameEventReceived) {
      final Set<String> bindingLibraryNames = new HashSet<>();
      bindingLibraryNames.add("package:flutter/src/widgets/binding.dart");
      bindingLibraryNames.add("package:flutter_web/src/widgets/binding.dart");

      final EvalOnDartLibrary flutterLibrary = new EvalOnDartLibrary(
        bindingLibraryNames,
        vmService,
        this
      );
      flutterLibrary.eval("WidgetsBinding.instance.debugDidSendFirstFrameEvent", null, null).whenCompleteAsync((v, e) -> {
        // If there is an error we assume the first frame has been received.
        final boolean didSendFirstFrameEvent = e == null ||
                                               v == null ||
                                               Objects.equals(v.getValueAsString(), "true");
        if (didSendFirstFrameEvent) {
          onFrameEventReceived();
        }
      });
    }
    if (isolate.getExtensionRPCs() != null) {
      for (String extension : isolate.getExtensionRPCs()) {
        addServiceExtension(extension);
      }
    }
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

  private void onFlutterIsolateStopped() {
    final Iterable<EventStream<Boolean>> existingExtensions;
    synchronized (serviceExtensions) {
      firstFrameEventReceived = false;
      existingExtensions = new ArrayList<>(serviceExtensions.values());
    }
    for (EventStream<Boolean> serviceExtension : existingExtensions) {
      serviceExtension.setValue(false);
    }
  }

  @SuppressWarnings("EmptyMethod")
  private void onVmServiceReceived(String streamId, Event event) {
    // Check for the current Flutter isolate exiting.
    final IsolateRef flutterIsolateRef = flutterIsolateRefStream.getValue();
    if (flutterIsolateRef != null) {
      if (event.getKind() == EventKind.IsolateExit && StringUtil.equals(event.getIsolate().getId(), flutterIsolateRef.getId())) {
        setFlutterIsolate(null);
        onFlutterIsolateStopped();
      }
    }

    final String kind = event.getExtensionKind();

    if (event.getKind() == EventKind.Extension) {
      switch (kind) {
        case "Flutter.FirstFrame":
        case "Flutter.Frame":
          // Track whether we have received the first frame event and add pending service extensions if we have.
          onFrameEventReceived();
          break;
        case "Flutter.ServiceExtensionStateChanged":
          final JsonObject extensionData = event.getExtensionData().getJson();
          final String name = extensionData.get("extension").getAsString();
          final String valueFromJson = extensionData.get("value").getAsString();

          final ToggleableServiceExtensionDescription extension = ServiceExtensions.toggleableExtensionsWhitelist.get(name);
          if (extension != null) {
            final Object value = getExtensionValueFromEventJson(name, valueFromJson);
            final boolean enabled = value.equals(extension.getEnabledValue());
            setServiceExtensionState(name, enabled, value);
          }
          break;
        case "Flutter.Error":
          app.getFlutterConsoleLogManager().handleFlutterErrorEvent(event);
          break;
      }
    }
    else if (event.getKind() == EventKind.ServiceExtensionAdded) {
      maybeAddServiceExtension(event.getExtensionRPC());
    }
    else if (StringUtil.equals(streamId, VmService.LOGGING_STREAM_ID) || StringUtil.equals(streamId, LOGGING_STREAM_ID_OLD)) {
      app.getFlutterConsoleLogManager().handleLoggingEvent(event);
    }

    // Check to see if there's a new Flutter isolate.
    if (flutterIsolateRefStream.getValue() == null) {
      // Check for service extension registrations.
      if (event.getKind() == EventKind.ServiceExtensionAdded) {
        final String extensionName = event.getExtensionRPC();

        if (extensionName.startsWith(ServiceExtensions.flutterPrefix)) {
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

  private Object getExtensionValueFromEventJson(String name, String valueFromJson) {
    final Object enabledValue =
      ServiceExtensions.toggleableExtensionsWhitelist.get(name).getEnabledValue();

    if (enabledValue instanceof Boolean) {
      return valueFromJson.equals("true");
    }
    else if (enabledValue instanceof Double) {
      return Double.valueOf(valueFromJson);
    }
    else {
      return valueFromJson;
    }
  }

  private void maybeAddServiceExtension(String name) {
    synchronized (serviceExtensions) {
      if (firstFrameEventReceived) {
        addServiceExtension(name);
        assert (pendingServiceExtensions.isEmpty());
      }
      else {
        pendingServiceExtensions.add(name);
      }
    }
  }

  private void onFrameEventReceived() {
    synchronized (serviceExtensions) {
      if (firstFrameEventReceived) {
        // The first frame event was already received.
        return;
      }
      firstFrameEventReceived = true;

      for (String extensionName : pendingServiceExtensions) {
        addServiceExtension(extensionName);
      }
      pendingServiceExtensions.clear();
    }
  }

  private void addServiceExtension(String name) {
    synchronized (serviceExtensions) {
      final EventStream<Boolean> stream = serviceExtensions.get(name);
      if (stream == null) {
        serviceExtensions.put(name, new EventStream<>(true));
      }
      else if (!stream.getValue()) {
        stream.setValue(true);
      }

      // Set any extensions that are already enabled on the device. This will
      // enable extension states for default-enabled extensions and extensions
      // enabled before attaching.
      restoreExtensionFromDevice(name);

      // Restore any previously true states by calling their service extensions.
      if (getServiceExtensionState(name).getValue().isEnabled()) {
        restoreServiceExtensionState(name);
      }
    }
  }

  private void restoreExtensionFromDevice(String name) {
    if (!ServiceExtensions.toggleableExtensionsWhitelist.containsKey(name)) {
      return;
    }
    final Object enabledValue =
      ServiceExtensions.toggleableExtensionsWhitelist.get(name).getEnabledValue();

    final CompletableFuture<JsonObject> response = app.callServiceExtension(name);
    response.thenApply(obj -> {
      Object value = null;
      if (obj != null) {
        if (enabledValue instanceof Boolean) {
          value = obj.get("enabled").getAsString().equals("true");
          maybeRestoreExtension(name, value);
        }
        else if (enabledValue instanceof String) {
          value = obj.get("value").getAsString();
          maybeRestoreExtension(name, value);
        }
        else if (enabledValue instanceof Double) {
          value = Double.parseDouble(obj.get("value").getAsString());
          maybeRestoreExtension(name, value);
        }
      }
      return value;
    });
  }

  private void maybeRestoreExtension(String name, Object value) {
    if (value.equals(ServiceExtensions.toggleableExtensionsWhitelist.get(name).getEnabledValue())) {
      setServiceExtensionState(name, true, value);
    }
  }

  private void restoreServiceExtensionState(String name) {
    if (app.isSessionActive()) {
      if (StringUtil.equals(name, ServiceExtensions.toggleSelectWidgetMode.getExtension())) {
        // Do not call the service extension for this extension. We do not want to persist showing the
        // inspector on app restart.
        return;
      }

      final Object value = getServiceExtensionState(name).getValue().getValue();

      if (value instanceof Boolean) {
        app.callBooleanExtension(name, (Boolean)value);
      }
      else if (value instanceof String) {
        final Map<String, Object> params = new HashMap<>();
        params.put("value", value);
        app.callServiceExtension(name, params);
      }
      else if (value instanceof Double) {
        final Map<String, Object> params = new HashMap<>();
        // The param name for a numeric service extension will be the last part of the extension name
        // (ext.flutter.extensionName => extensionName).
        params.put(name.substring(name.lastIndexOf(".") + 1), value);
        app.callServiceExtension(name, params);
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
    final EventStream<Boolean> stream = getStream(name, serviceExtensions);
    return stream.listen(onData, true);
  }

  @NotNull
  private EventStream<Boolean> getStream(String name, Map<String, EventStream<Boolean>> streamMap) {
    EventStream<Boolean> stream;
    synchronized (streamMap) {
      stream = streamMap.get(name);
      if (stream == null) {
        stream = new EventStream<>(false);
        streamMap.put(name, stream);
      }
    }
    return stream;
  }

  public @NotNull
  EventStream<ServiceExtensionState> getServiceExtensionState(String name) {
    return getStateStream(name, serviceExtensionState);
  }

  @NotNull
  private EventStream<ServiceExtensionState> getStateStream(
    String name, Map<String, EventStream<ServiceExtensionState>> streamMap) {
    EventStream<ServiceExtensionState> stream;
    synchronized (streamMap) {
      stream = streamMap.get(name);
      if (stream == null) {
        stream = new EventStream<>(new ServiceExtensionState(false, null));
        streamMap.put(name, stream);
      }
    }
    return stream;
  }

  public void setServiceExtensionState(String name, boolean enabled, Object value) {
    final EventStream<ServiceExtensionState> stream = getServiceExtensionState(name);
    stream.setValue(new ServiceExtensionState(enabled, value));
  }

  /**
   * Returns whether a service extension matching the specified name has
   * already been registered.
   * <p>
   * If the service extension may be registered at some point in the future it
   * is bests use hasServiceExtension as well to listen for changes in whether
   * the extension is present.
   */
  public boolean hasServiceExtensionNow(String name) {
    synchronized (serviceExtensions) {
      final EventStream<Boolean> stream = serviceExtensions.get(name);
      return stream != null && stream.getValue() == Boolean.TRUE;
    }
  }

  public void hasServiceExtension(String name, Consumer<Boolean> onData, Disposable parentDisposable) {
    Disposer.register(parentDisposable, hasServiceExtension(name, onData));
  }

  public void addPollingClient() {
    polledCount++;
    resumePolling();
  }

  public void removePollingClient() {
    if (polledCount > 0) polledCount--;
    pausePolling();
  }

  private boolean anyPollingClients() {
    return polledCount > 0;
  }

  private void pausePolling() {
    if (isRunning && !anyPollingClients()) {
      heapMonitor.pausePolling();
    }
  }

  private void resumePolling() {
    if (isRunning && anyPollingClients()) {
      heapMonitor.resumePolling();
    }
  }

  @Override
  public void stateChanged(FlutterApp.State newState) {
    if (newState == FlutterApp.State.RESTARTING) {
      // The set of service extensions available may be different once the app
      // restarts and no service extensions will be available until the app is
      // suitably far along in the restart process. It turns out the
      // IsolateExit event cannot be relied on to track when a restart is
      // occurring for unclear reasons.
      onFlutterIsolateStopped();
    }
  }
}

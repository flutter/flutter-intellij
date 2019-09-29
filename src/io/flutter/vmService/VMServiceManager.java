/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import org.dartlang.vm.service.VmServiceListener;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.ServiceExtensionConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static io.flutter.vmService.ServiceExtensions.enableOnDeviceInspector;

public class VMServiceManager implements FlutterApp.FlutterAppListener, Disposable {
  public final double defaultRefreshRate = 60.0;

  private static final Logger LOG = Logger.getInstance(VMServiceManager.class);

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

  private final EventStream<Double> displayRefreshRateStream;

  private volatile boolean firstFrameEventReceived = false;
  private final VmService vmService;

  /**
   * Temporarily stores service extensions that we need to add. We should not add extensions until the first frame event
   * has been received [firstFrameEventReceived].
   */
  private final List<String> pendingServiceExtensions = new ArrayList<>();

  private final VmServiceListener myVmServiceListener;
  private final Set<String> registeredServices = new HashSet<>();

  public VMServiceManager(@NotNull FlutterApp app, @NotNull VmService vmService) {
    this.app = app;
    this.vmService = vmService;
    app.addStateListener(this);

    assert (app.getFlutterDebugProcess() != null);

    this.heapMonitor = new HeapMonitor(app.getFlutterDebugProcess().getVmServiceWrapper());
    this.flutterFramesMonitor = new FlutterFramesMonitor(this, vmService);
    flutterIsolateRefStream = new EventStream<>();
    displayRefreshRateStream = new EventStream<>();

    // The VM Service depends on events from the Extension event stream to determine when Flutter.Frame
    // events are fired. Without the call to listen, events from the stream will not be sent.
    vmService.streamListen(VmService.EXTENSION_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    vmService.streamListen(VmService.LOGGING_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    vmService.streamListen(VmService.SERVICE_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    myVmServiceListener = new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        onVmServiceReceived(streamId, event);
      }

      @Override
      public void connectionClosed() {
        onVmConnectionClosed();
      }
    };
    vmService.addVmServiceListener(myVmServiceListener);

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

    setServiceExtensionState(enableOnDeviceInspector.getExtension(), true, true);
  }

  @NotNull
  public HeapMonitor getHeapMonitor() {
    return heapMonitor;
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
        Disposer.dispose(flutterLibrary);
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
  private void startHeapMonitor() {
    // Start polling.
    heapMonitor.start();
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
   * be notified when the Flutter isolate is discovered should prefer the StreamSubscription variant of this
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
  private void stopHeapMonitor() {
    heapMonitor.stop();
  }

  @Override
  public void dispose() {
    onVmConnectionClosed();
  }

  private void onVmConnectionClosed() {
    heapMonitor.stop();
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

          final ServiceExtensionDescription extension = ServiceExtensions.toggleableExtensionsWhitelist.get(name);
          if (extension != null) {
            final Object value = getExtensionValueFromEventJson(name, valueFromJson);
            if (extension instanceof ToggleableServiceExtensionDescription) {
              final ToggleableServiceExtensionDescription toggleableExtension = (ToggleableServiceExtensionDescription)extension;
              setServiceExtensionState(name, value.equals(toggleableExtension.getEnabledValue()), value);
            }
            else {
              setServiceExtensionState(name, true, value);
            }
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
    else if (StringUtil.equals(streamId, VmService.LOGGING_STREAM_ID)) {
      app.getFlutterConsoleLogManager().handleLoggingEvent(event);
    }
    else if (event.getKind() == EventKind.ServiceRegistered) {
      registerService(event.getService());
    }
    else if (event.getKind() == EventKind.ServiceUnregistered) {
      unregisterService(event.getService());
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
  }

  private Object getExtensionValueFromEventJson(String name, String valueFromJson) {
    final Class valueClass =
      ServiceExtensions.toggleableExtensionsWhitelist.get(name).getValueClass();

    if (valueClass == Boolean.class) {
      return valueFromJson.equals("true");
    }
    else if (valueClass == Double.class) {
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

      // Query for display refresh rate and add the value to the stream.
      // This needs to happen on the UI thread.
      //noinspection CodeBlock2Expr
      ApplicationManager.getApplication().invokeLater(() -> {
        getDisplayRefreshRate().thenAcceptAsync(displayRefreshRateStream::setValue);
      });
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
    final Class valueClass =
      ServiceExtensions.toggleableExtensionsWhitelist.get(name).getValueClass();

    final CompletableFuture<JsonObject> response = app.callServiceExtension(name);
    response.thenApply(obj -> {
      Object value = null;
      if (obj != null) {
        if (valueClass == Boolean.class) {
          value = obj.get("enabled").getAsString().equals("true");
          maybeRestoreExtension(name, value);
        }
        else if (valueClass == String.class) {
          value = obj.get("value").getAsString();
          maybeRestoreExtension(name, value);
        }
        else if (valueClass == Double.class) {
          value = Double.parseDouble(obj.get("value").getAsString());
          maybeRestoreExtension(name, value);
        }
      }
      return value;
    });
  }

  private void maybeRestoreExtension(String name, Object value) {
    if (ServiceExtensions.toggleableExtensionsWhitelist.get(name) instanceof ToggleableServiceExtensionDescription) {
      final ToggleableServiceExtensionDescription extensionDescription =
        (ToggleableServiceExtensionDescription)ServiceExtensions.toggleableExtensionsWhitelist.get(name);
      if (value.equals(extensionDescription.getEnabledValue())) {
        setServiceExtensionState(name, true, value);
      }
    }
    else {
      setServiceExtensionState(name, true, value);
    }
  }

  private void restoreServiceExtensionState(String name) {
    if (app.isSessionActive()) {
      if (StringUtil.equals(name, ServiceExtensions.toggleOnDeviceWidgetInspector.getExtension())) {
        // Do not call the service extension for this extension. We do not want to persist showing the
        // inspector on app restart.
        return;
      }

      @Nullable final Object value = getServiceExtensionState(name).getValue().getValue();

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
      startHeapMonitor();
    }
  }

  /**
   * Remove a heap listener.
   */
  public void removeHeapListener(@NotNull HeapListener listener) {
    heapMonitor.removeListener(listener);
    if (!heapMonitor.hasListeners()) {
      stopHeapMonitor();
    }
  }

  @NotNull
  public StreamSubscription<Boolean> hasServiceExtension(String name, Consumer<Boolean> onData) {
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

  @NotNull
  public EventStream<ServiceExtensionState> getServiceExtensionState(String name) {
    EventStream<ServiceExtensionState> stream;
    synchronized (serviceExtensionState) {
      stream = serviceExtensionState.get(name);
      if (stream == null) {
        stream = new EventStream<>(new ServiceExtensionState(false, null));
        serviceExtensionState.put(name, stream);
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
    if (!Disposer.isDisposed(parentDisposable)) {
      Disposer.register(parentDisposable, hasServiceExtension(name, onData));
    }
  }

  public boolean hasRegisteredService(String name) {
    return registeredServices.contains(name);
  }

  public boolean hasAnyRegisteredServices() {
    return !registeredServices.isEmpty();
  }

  private void registerService(String serviceName) {
    if (serviceName != null) {
      registeredServices.add(serviceName);
    }
  }

  private void unregisterService(String serviceName) {
    if (serviceName != null) {
      registeredServices.remove(serviceName);
    }
  }

  public int getTargetMicrosPerFrame() {
    Double fps = getCurrentDisplayRefreshRateRaw();
    if (fps == null) {
      fps = defaultRefreshRate;
    }
    return (int)Math.round((Math.floor(1000000.0f / fps)));
  }

  /**
   * Returns a StreamSubscription providing the queried display refresh rate.
   * <p>
   * The current value of the subscription can be null occasionally during initial application startup and for a brief time when doing a
   * hot restart.
   */
  public StreamSubscription<Double> getCurrentDisplayRefreshRate(Consumer<Double> onValue, boolean onUIThread) {
    return displayRefreshRateStream.listen(onValue, onUIThread);
  }

  /**
   * Return the current display refresh rate, if any.
   * <p>
   * Note that this may not be immediately populated at app startup for Flutter apps. In that case, this will return
   * the default value (defaultRefreshRate). Clients that wish to be notified when the refresh rate is discovered
   * should prefer the StreamSubscription variant of this method (getCurrentDisplayRefreshRate()).
   */
  public Double getCurrentDisplayRefreshRateRaw() {
    synchronized (displayRefreshRateStream) {
      Double fps = displayRefreshRateStream.getValue();
      if (fps == null) {
        fps = defaultRefreshRate;
      }
      return fps;
    }
  }

  public CompletableFuture<Double> getDisplayRefreshRate() {
    final double unknownRefreshRate = 0.0;

    final String flutterViewId = getFlutterViewId().exceptionally(exception -> {
      // We often see "java.lang.RuntimeException: Method not found" from here; perhaps a race condition?
      LOG.warn(exception.getMessage());
      return null;
    }).join();

    if (flutterViewId == null) {
      // Fail gracefully by returning the default.
      return CompletableFuture.completedFuture(defaultRefreshRate);
    }

    return invokeGetDisplayRefreshRate(flutterViewId);
  }

  private CompletableFuture<Double> invokeGetDisplayRefreshRate(String flutterViewId) {
    final CompletableFuture<Double> ret = new CompletableFuture<>();
    final JsonObject params = new JsonObject();
    params.addProperty("viewId", flutterViewId);
    vmService.callServiceExtension(
      getCurrentFlutterIsolateRaw().getId(), ServiceExtensions.displayRefreshRate, params,
      new ServiceExtensionConsumer() {
        @Override
        public void onError(RPCError error) {
          ret.completeExceptionally(new RuntimeException(error.getMessage()));
        }

        @Override
        public void received(JsonObject object) {
          if (object == null) {
            ret.complete(null);
          }
          else {
            ret.complete(object.get("fps").getAsDouble());
          }
        }
      }
    );
    return ret;
  }

  private CompletableFuture<String> getFlutterViewId() {
    return getFlutterViewsList().exceptionally(exception -> {
      throw new RuntimeException(exception.getMessage());
    }).thenApplyAsync((JsonElement element) -> {
      final JsonArray viewsList = element.getAsJsonObject().get("views").getAsJsonArray();
      for (JsonElement jsonElement : viewsList) {
        final JsonObject view = jsonElement.getAsJsonObject();
        if (view.get("type").getAsString().equals("FlutterView")) {
          return view.get("id").getAsString();
        }
      }
      throw new RuntimeException("No Flutter views to query: " + viewsList.toString());
    });
  }

  private CompletableFuture<JsonElement> getFlutterViewsList() {
    final CompletableFuture<JsonElement> ret = new CompletableFuture<>();
    final IsolateRef currentFlutterIsolate = getCurrentFlutterIsolateRaw();
    if (currentFlutterIsolate == null) {
      ret.completeExceptionally(new RuntimeException("No isolate to query for Flutter views."));
      return ret;
    }
    final String isolateId = getCurrentFlutterIsolateRaw().getId();
    vmService.callServiceExtension(
      isolateId,
      ServiceExtensions.flutterListViews,
      new ServiceExtensionConsumer() {
        @Override
        public void onError(RPCError error) {
          String message = isolateId;
          message += ":" + ServiceExtensions.flutterListViews;
          message += ":" + error.getCode();
          message += ":" + error.getMessage();
          if (error.getDetails() != null) {
            message += ":" + error.getDetails();
          }
          ret.completeExceptionally(new RuntimeException(message));
        }

        @Override
        public void received(JsonObject object) {
          if (object == null) {
            ret.complete(null);
          }
          else {
            ret.complete(object);
          }
        }
      }
    );
    return ret;
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

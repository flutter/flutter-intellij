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
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
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
  @NotNull private final FlutterApp app;
  @NotNull private final Map<String, EventStream<Boolean>> serviceExtensions = new HashMap<>();

  /**
   * Boolean value applicable only for boolean service extensions indicating
   * whether the service extension is enabled or disabled.
   */
  @NotNull private final Map<String, EventStream<ServiceExtensionState>> serviceExtensionState = new HashMap<>();

  private final EventStream<IsolateRef> flutterIsolateRefStream;

  private volatile boolean firstFrameEventReceived = false;
  private final VmService vmService;

  /**
   * Temporarily stores service extensions that we need to add. We should not add extensions until the first frame event
   * has been received [firstFrameEventReceived].
   */
  private final List<String> pendingServiceExtensions = new ArrayList<>();

  private final Set<String> registeredServices = new HashSet<>();

  public VMServiceManager(@NotNull FlutterApp app, @NotNull VmService vmService) {
    this.app = app;
    this.vmService = vmService;
    app.addStateListener(this);

    assert (app.getFlutterDebugProcess() != null);

    flutterIsolateRefStream = new EventStream<>();

    // The VM Service depends on events from the Extension event stream to determine when Flutter.Frame
    // events are fired. Without the call to listen, events from the stream will not be sent.
    vmService.streamListen(VmService.EXTENSION_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    vmService.streamListen(VmService.LOGGING_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    vmService.streamListen(VmService.SERVICE_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    final VmServiceListener myVmServiceListener = new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        onVmServiceReceived(streamId, event);
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
              addRegisteredExtensionRPCs(isolate);
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

  public void addRegisteredExtensionRPCs(Isolate isolate) {
    if (isolate.getExtensionRPCs() != null) {
      for (String extension : isolate.getExtensionRPCs()) {
        addServiceExtension(extension);
      }
    }
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

  @Override
  public void dispose() {
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

          final ServiceExtensionDescription extension = ServiceExtensions.toggleableExtensionsAllowList.get(name);
          if (extension != null) {
            final Object value = getExtensionValueFromEventJson(name, valueFromJson);
            if (extension instanceof ToggleableServiceExtensionDescription toggleableExtension) {
              setServiceExtensionState(name, Objects.equals(value, toggleableExtension.getEnabledValue()), value);
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
      ServiceExtensions.toggleableExtensionsAllowList.get(name).getValueClass();

    if (valueClass == Boolean.class) {
      return Objects.equals(valueFromJson, "true");
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
      if (getServiceExtensionState(name).getValue().enabled()) {
        restoreServiceExtensionState(name);
      }
    }
  }

  private void restoreExtensionFromDevice(String name) {
    if (!ServiceExtensions.toggleableExtensionsAllowList.containsKey(name)) {
      return;
    }
    final Class valueClass =
      ServiceExtensions.toggleableExtensionsAllowList.get(name).getValueClass();

    final CompletableFuture<JsonObject> response = app.callServiceExtension(name);
    response.thenApply(obj -> {
      Object value = null;
      if (obj != null) {
        if (valueClass == Boolean.class) {
          value = Objects.equals(obj.get("enabled").getAsString(), "true");
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
    if (ServiceExtensions.toggleableExtensionsAllowList.get(name) instanceof ToggleableServiceExtensionDescription extensionDescription) {
      if (Objects.equals(value, extensionDescription.getEnabledValue())) {
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

      @Nullable final Object value = getServiceExtensionState(name).getValue().value();

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

  public CompletableFuture<String> getInspectorViewId() {
    return getFlutterViewsList().exceptionally(exception -> {
      throw new RuntimeException(exception.getMessage());
    }).thenApplyAsync((JsonElement element) -> {
      final JsonArray viewsList = element.getAsJsonObject().get("views").getAsJsonArray();
      for (JsonElement jsonElement : viewsList) {
        final JsonObject view = jsonElement.getAsJsonObject();
        if (Objects.equals(view.get("type").getAsString(), "InspectorView")) {
          return view.get("id").getAsString();
        }
      }
      throw new RuntimeException("No Flutter views to query: " + viewsList);
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
          ret.complete(object);
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

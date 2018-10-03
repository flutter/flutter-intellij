/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonObject;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.server.vmService.VMServiceManager;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.VmServiceListener;
import org.dartlang.vm.service.consumer.ServiceExtensionConsumer;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.RPCError;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;


public class VmServiceWidgetPerfProvider implements WidgetPerfProvider {
  private static final String GET_PERF_SOURCE_REPORTS_EXTENSION = "ext.flutter.inspector.getPerfSourceReports";
  @NotNull final FlutterApp.FlutterAppListener appListener;
  @NotNull final FlutterApp app;
  private VmServiceListener vmServiceListener;
  private Repaintable repaintable;
  private boolean started;
  private boolean isStarted;
  private boolean isDisposed = false;
  private boolean connected;
  private StreamSubscription<IsolateRef> isolateRefStreamSubscription;

  VmServiceWidgetPerfProvider(@NotNull FlutterApp app) {
    this.app = app;
    // start listening for frames, reload and restart events
    appListener = new FlutterApp.FlutterAppListener() {
      @Override
      public void stateChanged(FlutterApp.State newState) {
        if (!started && app.isStarted()) {
          started = true;
          requestRepaint(When.now);
        }
      }

      @Override
      public void notifyAppReloaded() {
        requestRepaint(When.now);
      }

      @Override
      public void notifyAppRestarted() {
        requestRepaint(When.now);
      }

      @Override
      public void notifyFrameRendered() {
        requestRepaint(When.soon);
      }

      public void notifyVmServiceAvailable(VmService vmService) {
        setupConnection(vmService);
      }
    };

    app.addStateListener(appListener);

    if (app.getVmService() != null) {
      setupConnection(app.getVmService());
    }
    started = app.isStarted();
  }

  public boolean isStarted() {
    return started;
  }

  public void setTarget(Repaintable repaintable) {
    this.repaintable = repaintable;
  }

  private void requestRepaint(When now) {
    if (repaintable != null) {
      repaintable.requestRepaint(now);
    }
  }

  @Override
  public void dispose() {
    app.removeStateListener(appListener);

    if (isolateRefStreamSubscription != null) {
      isolateRefStreamSubscription.dispose();
    }
    isDisposed = true;
    connected = false;

    // TODO(devoncarew): This method will be available in a future version of the service protocol library.
    //if (vmServiceListener != null) {
    //  app.getVmService().removeEventListener(vmServiceListener);
    //}
  }

  private void setupConnection(@NotNull VmService vmService) {
    if (isDisposed || connected) {
      return;
    }

    final VMServiceManager vmServiceManager = app.getVMServiceManager();
    assert vmServiceManager != null;

    connected = true;

    isolateRefStreamSubscription = vmServiceManager.getCurrentFlutterIsolate(
      (isolateRef) -> requestRepaint(When.soon), false);

    vmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        onVmServiceReceived(streamId, event);
      }

      @Override
      public void connectionClosed() {
      }
    });

    requestRepaint(When.soon);
  }

  private IsolateRef getCurrentIsolateRef() {
    assert app.getVMServiceManager() != null;
    return app.getVMServiceManager().getCurrentFlutterIsolateRaw();
  }

  public boolean isConnected() {
    return connected;
  }

  @Override
  public CompletableFuture<JsonObject> getPerfSourceReports(List<String> uris) {
    final JsonObject params = new JsonObject();
    for (int i = 0; i < uris.size(); ++i) {
      params.addProperty("arg" + i, uris.get(i));
    }

    final VmService vmService = app.getVmService();
    assert vmService != null;
    assert app.getVMServiceManager() != null;
    final IsolateRef isolateRef = app.getVMServiceManager().getCurrentFlutterIsolateRaw();

    final CompletableFuture<JsonObject> future = new CompletableFuture<>();
    if (!app.getVMServiceManager().hasServiceExtensionNow(GET_PERF_SOURCE_REPORTS_EXTENSION)) {
      return CompletableFuture.completedFuture(null);
    }
    vmService
      .callServiceExtension(isolateRef.getId(), GET_PERF_SOURCE_REPORTS_EXTENSION, params, new ServiceExtensionConsumer() {
        @Override
        public void received(JsonObject object) {
          future.complete(object);
        }

        @Override
        public void onError(RPCError error) {
          future.completeExceptionally(new RuntimeException(error.toString()));
        }
      });
    return future;
  }

  @Override
  public boolean shouldDisplayPerfStats(FileEditor editor) {
    return !app.isReloading() && app.isLatestVersionRunning(editor.getFile()) && !editor.isModified();
  }

  private void onVmServiceReceived(String streamId, Event event) {
    // TODO(jacobr): centrailize checks for Flutter.Frame
    // They are now in VMServiceManager, InspectorService, and here.
    if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {
      if (StringUtil.equals("Flutter.Frame", event.getExtensionKind())) {
        final JsonObject extensionData = event.getExtensionData().getJson();
        requestRepaint(When.soon);
      }
    }
  }
}

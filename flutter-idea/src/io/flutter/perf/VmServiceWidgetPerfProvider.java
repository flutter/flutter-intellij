/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonObject;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import io.flutter.vmService.VMServiceManager;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.VmServiceListener;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.IsolateRef;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class VmServiceWidgetPerfProvider implements WidgetPerfProvider {
  @NotNull final FlutterApp.FlutterAppListener appListener;
  @NotNull final FlutterApp app;
  private VmServiceListener vmServiceListener;
  private WidgetPerfListener target;
  private boolean started;
  private boolean isStarted;
  private boolean isDisposed = false;
  private boolean connected;
  private StreamSubscription<IsolateRef> isolateRefStreamSubscription;
  private CompletableFuture<InspectorService> inspectorService;

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

  public void setTarget(WidgetPerfListener widgetPerfListener) {
    this.target = widgetPerfListener;
  }

  private void requestRepaint(When now) {
    if (target != null) {
      target.requestRepaint(now);
    }
  }

  private void onWidgetPerfEvent(PerfReportKind kind, JsonObject json) {
    if (target != null) {
      target.onWidgetPerfEvent(kind, json);
    }
  }

  private void onNavigation() {
    if (target != null) {
      target.onNavigation();
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

    if (vmServiceListener != null && app.getVmService() != null) {
      app.getVmService().removeVmServiceListener(vmServiceListener);
    }
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

    inspectorService = InspectorService.create(app, app.getFlutterDebugProcess(), app.getVmService());
    inspectorService.whenCompleteAsync((service, throwable) -> Disposer.register(this, service));

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
  public boolean shouldDisplayPerfStats(FileEditor editor) {
    return !app.isReloading() && app.isLatestVersionRunning(editor.getFile()) && !editor.isModified();
  }

  @Override
  public CompletableFuture<DiagnosticsNode> getWidgetTree() {
    return inspectorService
      .thenComposeAsync((inspectorService) -> inspectorService.createObjectGroup("widget_perf").getSummaryTreeWithoutIds());
  }

  private void onVmServiceReceived(String streamId, Event event) {
    // TODO(jacobr): centrailize checks for Flutter.Frame
    // They are now in VMServiceManager, InspectorService, and here.
    if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {
      final String kind = event.getExtensionKind();
      if (kind == null) {
        return;
      }
      switch (kind) {
        case "Flutter.Frame":
          final JsonObject extensionData = event.getExtensionData().getJson();
          requestRepaint(When.soon);
          break;
        case "Flutter.RebuiltWidgets":
          onWidgetPerfEvent(PerfReportKind.rebuild, event.getExtensionData().getJson());
          break;
        case "Flutter.RepaintedWidgets":
          onWidgetPerfEvent(PerfReportKind.repaint, event.getExtensionData().getJson());
          break;
        case "Flutter.Navigation":
          onNavigation();
          break;
      }
    }
  }
}

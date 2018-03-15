/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import io.flutter.perf.PerfService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.inspector.InspectorService;
import org.dartlang.vm.service.VmService;
import org.jetbrains.annotations.NotNull;

/**
 * Coordinates communication on the message bus.
 */
public class FlutterViewMessages {
  public static final Topic<FlutterDebugNotifier> FLUTTER_DEBUG_TOPIC = Topic.create("flutter.debugActive", FlutterDebugNotifier.class);

  public interface FlutterDebugNotifier {
    void debugActive(FlutterDebugEvent event);
  }

  public static class FlutterDebugEvent {
    public final @NotNull FlutterApp app;
    public final @NotNull VmService vmService;

    FlutterDebugEvent(@NotNull FlutterApp app,
                      @NotNull VmService vmService) {
      this.app = app;
      this.vmService = vmService;
    }
  }

  public static void sendDebugActive(@NotNull Project project,
                                     @NotNull FlutterApp app,
                                     @NotNull VmService vmService) {
    final MessageBus bus = project.getMessageBus();
    final FlutterDebugNotifier publisher = bus.syncPublisher(FLUTTER_DEBUG_TOPIC);
    app.setVmService(vmService);
    assert(app.getFlutterDebugProcess() != null);
    app.setInspectorService( new InspectorService(app.getFlutterDebugProcess(), vmService));
    app.setPerfService(new PerfService(app.getFlutterDebugProcess(), vmService));
    publisher.debugActive(new FlutterDebugEvent(app, vmService));
  }
}

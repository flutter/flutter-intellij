/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceWrapper;
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
    public final @NotNull ObservatoryConnector observatoryConnector;
    public final @NotNull VmServiceWrapper vmServiceWrapper;
    public final @NotNull VmService vmService;

    FlutterDebugEvent(@NotNull ObservatoryConnector observatoryConnector,
                      @NotNull VmServiceWrapper vmServiceWrapper,
                      @NotNull VmService vmService) {
      this.observatoryConnector = observatoryConnector;
      this.vmServiceWrapper = vmServiceWrapper;
      this.vmService = vmService;
    }
  }

  public static void sendDebugActive(@NotNull Project project,
                                     @NotNull ObservatoryConnector observatoryConnector,
                                     @NotNull VmServiceWrapper vmServiceWrapper,
                                     @NotNull VmService vmService) {
    final MessageBus bus = project.getMessageBus();
    final FlutterDebugNotifier publisher = bus.syncPublisher(FLUTTER_DEBUG_TOPIC);
    publisher.debugActive(new FlutterDebugEvent(observatoryConnector, vmServiceWrapper, vmService));
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Coordinates communication on the message bus.
 */
public class FlutterViewMessages {
  public static Topic<FlutterDebugNotifier> FLUTTER_DEBUG_TOPIC = Topic.create("flutter.debugActive", FlutterDebugNotifier.class);

  public interface FlutterDebugNotifier {
    void debugActive(VmServiceWrapper vmServiceWrapper);
  }

  // TODO: VmServiceWrapper or VmService?
  public static void sendDebugActive(@NotNull VmServiceWrapper vmServiceWrapper) {
    final MessageBus bus = ApplicationManager.getApplication().getMessageBus();
    final FlutterDebugNotifier publisher = bus.syncPublisher(FLUTTER_DEBUG_TOPIC);
    publisher.debugActive(vmServiceWrapper);
  }
}

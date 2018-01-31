/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FlutterDartAnalysisServer {
  private static final String FLUTTER_NOTIFICATION_OUTLINE = "flutter.outline";

  @Nullable
  private DartAnalysisServerServiceEx dartServiceEx;

  /**
   * Each key is a notification identifier.
   * Each value is the set of files subscribed to the notification.
   */
  private final Map<String, List<String>> subscriptions = new HashMap<>();

  private final Map<String, List<FlutterOutlineListener>> fileOutlineListeners = new HashMap<>();

  @NotNull
  public static FlutterDartAnalysisServer getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, FlutterDartAnalysisServer.class);
  }

  private FlutterDartAnalysisServer(@NotNull Project project) {
    JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
      final DartAnalysisServerService analysisService = DartPlugin.getInstance().getAnalysisService(project);
      final DartAnalysisServerServiceEx analysisServiceEx = DartAnalysisServerServiceEx.get(analysisService);
      if (analysisServiceEx != null && analysisServiceEx != dartServiceEx) {
        dartServiceEx = analysisServiceEx;
        dartServiceEx.addListener(FlutterDartAnalysisServer.this::processNotification);
        sendSubscriptions();
      }
    }, 100, 100, TimeUnit.MILLISECONDS);
  }

  public void addOutlineListener(@NotNull final String filePath, @NotNull final FlutterOutlineListener listener) {
    final List<FlutterOutlineListener> listeners = fileOutlineListeners.computeIfAbsent(filePath, k -> new ArrayList<>());
    if (listeners.add(listener)) {
      addSubscription(FlutterService.OUTLINE, filePath);
    }
  }

  public void removeOutlineListener(@NotNull final String filePath, @NotNull final FlutterOutlineListener listener) {
    final List<FlutterOutlineListener> listeners = fileOutlineListeners.get(filePath);
    if (listeners != null && listeners.remove(listener)) {
      removeSubscription(FlutterService.OUTLINE, filePath);
    }
  }

  private void addSubscription(@NotNull final String service, @NotNull final String filePath) {
    final List<String> files = subscriptions.computeIfAbsent(service, k -> new ArrayList<>());
    if (files.add(filePath)) {
      sendSubscriptions();
    }
  }

  private void removeSubscription(@NotNull final String service, @NotNull final String filePath) {
    final List<String> files = subscriptions.get(service);
    if (files != null && files.remove(filePath)) {
      sendSubscriptions();
    }
  }

  private void sendSubscriptions() {
    if (dartServiceEx != null) {
      final String id = dartServiceEx.generateUniqueId();
      dartServiceEx.sendRequest(FlutterRequestUtilities.generateAnalysisSetSubscriptions(id, subscriptions));
    }
  }

  /**
   * Attempts to handle the given {@link JsonObject} as a notification.
   */
  private void processNotification(JsonObject response) {
    final JsonElement eventElement = response.get("event");
    if (eventElement == null || !eventElement.isJsonPrimitive()) {
      return;
    }
    final String event = eventElement.getAsString();
    if (event.equals(FLUTTER_NOTIFICATION_OUTLINE)) {
      final JsonObject paramsObject = response.get("params").getAsJsonObject();
      final String file = paramsObject.get("file").getAsString();
      final JsonObject outlineObject = paramsObject.get("outline").getAsJsonObject();
      final FlutterOutline outline = FlutterOutline.fromJson(outlineObject);
      final List<FlutterOutlineListener> listeners = fileOutlineListeners.get(file);
      if (listeners != null) {
        for (FlutterOutlineListener listener : Lists.newArrayList(listeners)) {
          listener.outlineUpdated(file, outline);
        }
      }
    }
  }
}

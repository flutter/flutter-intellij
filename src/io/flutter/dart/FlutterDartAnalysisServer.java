/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterService;
<<<<<<< HEAD
=======
import org.dartlang.analysis.server.protocol.Outline;
>>>>>>> progress
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FlutterDartAnalysisServer {
  private static final String FLUTTER_NOTIFICATION_OUTLINE = "flutter.outline";

  private final DartAnalysisServerServiceEx dartServiceEx;

  /**
   * Each key is a notification identifier.
   * Each value is the set of files subscribed to the notification.
   */
  private final Map<String, List<String>> subscriptions = new HashMap<>();

<<<<<<< HEAD
  private final Map<String, List<FlutterOutlineListener>> fileOutlineListeners = new HashMap<>();

  public FlutterDartAnalysisServer(@NotNull DartAnalysisServerServiceEx dartServiceEx) {
    this.dartServiceEx = dartServiceEx;
    dartServiceEx.addListener(this::processNotification);
  }

  public void addOutlineListener(@NotNull final String filePath, @NotNull final FlutterOutlineListener listener) {
    final List<FlutterOutlineListener> listeners = fileOutlineListeners.computeIfAbsent(filePath, k -> new ArrayList<>());
=======
  private final Map<String, List<OutlineListener>> fileOutlineListeners = new HashMap<>();

  public FlutterDartAnalysisServer(DartAnalysisServerServiceEx dartServiceEx) {
    this.dartServiceEx = dartServiceEx;
    dartServiceEx.addListener(new DartAnalysisServerServiceExResponseListener() {
      @Override
      public void onResponse(JsonObject json) {
        processNotification(json);
      }
    });
  }

  public void addOutlineListener(@NotNull final String filePath, @NotNull final OutlineListener listener) {
    final List<OutlineListener> listeners = fileOutlineListeners.computeIfAbsent(filePath, k -> new ArrayList<>());
>>>>>>> progress
    if (listeners.add(listener)) {
      addSubscription(FlutterService.OUTLINE, filePath);
    }
  }

<<<<<<< HEAD
  public void removeOutlineListener(@NotNull final String filePath, @NotNull final FlutterOutlineListener listener) {
    final List<FlutterOutlineListener> listeners = fileOutlineListeners.get(filePath);
=======
  public void removeOutlineListener(@NotNull final String filePath, @NotNull final OutlineListener listener) {
    final List<OutlineListener> listeners = fileOutlineListeners.get(filePath);
>>>>>>> progress
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
    final String id = dartServiceEx.generateUniqueId();
    dartServiceEx.sendRequest(FlutterRequestUtilities.generateAnalysisSetSubscriptions(id, subscriptions));
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
<<<<<<< HEAD
      final List<FlutterOutlineListener> listeners = fileOutlineListeners.get(file);
      if (listeners != null) {
        for (FlutterOutlineListener listener : Lists.newArrayList(listeners)) {
          listener.outlineUpdated(file, outline);
        }
=======
      final List<OutlineListener> listeners = fileOutlineListeners.get(file);
      for (OutlineListener listener : Lists.newArrayList(listeners)) {
        listener.outlineUpdated(file, outline);
>>>>>>> progress
      }
    }
  }
}

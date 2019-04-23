/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.dart.server.AnalysisServerListenerAdapter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FlutterDartAnalysisServer {
  private static final String FLUTTER_DESIGN_TIME_CONSTRUCTOR = "flutter.getChangeAddForDesignTimeConstructor";
  private static final String FLUTTER_NOTIFICATION_OUTLINE = "flutter.outline";

  @NotNull final DartAnalysisServerService analysisService;

  /**
   * Each key is a notification identifier.
   * Each value is the set of files subscribed to the notification.
   */
  private final Map<String, List<String>> subscriptions = new HashMap<>();

  private final Map<String, List<FlutterOutlineListener>> fileOutlineListeners = new HashMap<>();

  /**
   * Each key is a request identifier.
   * Each value is the {@link Consumer} for the response.
   */
  private final Map<String, Consumer<JsonObject>> responseConsumers = new HashMap<>();

  @NotNull
  public static FlutterDartAnalysisServer getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, FlutterDartAnalysisServer.class);
  }

  private FlutterDartAnalysisServer(@NotNull Project project) {
    analysisService = DartPlugin.getInstance().getAnalysisService(project);
    analysisService.addResponseListener(FlutterDartAnalysisServer.this::processResponse);
    analysisService.addAnalysisServerListener(new AnalysisServerListenerAdapter() {
      @Override
      public void serverConnected(String s) {
        // If the server reconnected we need to let it know that we still care
        // about our subscriptions.
        if (!subscriptions.isEmpty()) {
          sendSubscriptions();
        }
      }
    });
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
    final String id = analysisService.generateUniqueId();
    analysisService.sendRequest(id, FlutterRequestUtilities.generateAnalysisSetSubscriptions(id, subscriptions));
  }

  @NotNull
  public List<SourceChange> edit_getAssists(@NotNull VirtualFile file, int offset, int length) {
    return analysisService.edit_getAssists(file, offset, length);
  }

  @Nullable
  public SourceChange flutter_getChangeAddForDesignTimeConstructor(@NotNull VirtualFile file, int _offset) {
    final String filePath = FileUtil.toSystemDependentName(file.getPath());
    final int offset = analysisService.getOriginalOffset(file, _offset);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<SourceChange> result = new AtomicReference<>();
    final String id = analysisService.generateUniqueId();
    responseConsumers.put(id, (resultObject) -> {
      try {
        final JsonObject changeObject = resultObject.getAsJsonObject("change");
        final SourceChange change = SourceChange.fromJson(changeObject);
        result.set(change);
      }
      catch (Throwable ignored) {
      }
      latch.countDown();
    });

    final JsonObject request = FlutterRequestUtilities.generateFlutterGetChangeAddForDesignTimeConstructor(id, filePath, offset);
    analysisService.sendRequest(id, request);

    Uninterruptibles.awaitUninterruptibly(latch, 100, TimeUnit.MILLISECONDS);
    return result.get();
  }

  /**
   * Handle the given {@link JsonObject} response.
   */
  private void processResponse(JsonObject response) {
    if (processNotification(response)) {
      return;
    }

    if (response.has("error")) {
      return;
    }

    final JsonObject resultObject = response.getAsJsonObject("result");
    if (resultObject == null) {
      return;
    }

    final JsonPrimitive idJsonPrimitive = (JsonPrimitive)response.get("id");
    if (idJsonPrimitive == null) {
      return;
    }
    final String idString = idJsonPrimitive.getAsString();

    final Consumer<JsonObject> consumer = responseConsumers.remove(idString);
    if (consumer == null) {
      return;
    }

    consumer.consume(resultObject);
  }

  /**
   * Attempts to handle the given {@link JsonObject} as a notification.
   */
  private boolean processNotification(JsonObject response) {
    final JsonElement eventElement = response.get("event");
    if (eventElement == null || !eventElement.isJsonPrimitive()) {
      return false;
    }
    final String event = eventElement.getAsString();
    if (event.equals(FLUTTER_NOTIFICATION_OUTLINE)) {
      final JsonObject paramsObject = response.get("params").getAsJsonObject();
      final String file = paramsObject.get("file").getAsString();

      final JsonElement instrumentedCodeElement = paramsObject.get("instrumentedCode");
      final String instrumentedCode = instrumentedCodeElement != null ? instrumentedCodeElement.getAsString() : null;

      final JsonObject outlineObject = paramsObject.get("outline").getAsJsonObject();
      final FlutterOutline outline = FlutterOutline.fromJson(outlineObject);

      final List<FlutterOutlineListener> listeners = fileOutlineListeners.get(file);
      if (listeners != null) {
        for (FlutterOutlineListener listener : Lists.newArrayList(listeners)) {
          listener.outlineUpdated(file, outline, instrumentedCode);
        }
      }
    }
    return true;
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.dart.server.AnalysisServerListenerAdapter;
import com.google.dart.server.ResponseListener;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterService;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlutterDartAnalysisServer {
  private static final String FLUTTER_NOTIFICATION_OUTLINE = "flutter.outline";

  @NotNull final DartAnalysisServerService analysisService;

  /**
   * Each key is a notification identifier.
   * Each value is the set of files subscribed to the notification.
   */
  private final Map<String, List<String>> subscriptions = new HashMap<>();

  @VisibleForTesting
  protected final Map<String, List<FlutterOutlineListener>> fileOutlineListeners = new HashMap<>();

  @NotNull
  public static FlutterDartAnalysisServer getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, FlutterDartAnalysisServer.class);
  }

  @VisibleForTesting
  public FlutterDartAnalysisServer(@NotNull Project project) {
    analysisService = DartPlugin.getInstance().getAnalysisService(project);
    analysisService.addResponseListener(new CompatibleResponseListener());
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

  private void processString(String jsonString) {
    processResponse(new Gson().fromJson(jsonString, JsonObject.class));
  }

  /**
   * Handle the given {@link JsonObject} response.
   */
  private void processResponse(JsonObject response) {
    processNotification(response);
  }

  /**
   * Attempts to handle the given {@link JsonObject} as a notification.
   */
  @SuppressWarnings("UnusedReturnValue")
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

  class CompatibleResponseListener implements ResponseListener {
    // TODO(anyone): Remove this once 192 is the minimum supported base.
    @SuppressWarnings({"override", "RedundantSuppression"})
    public void onResponse(JsonObject jsonObject) {
      processResponse(jsonObject);
    }

    @SuppressWarnings({"override", "RedundantSuppression"})
    public void onResponse(String jsonString) {
      processString(jsonString);
    }
  }
}

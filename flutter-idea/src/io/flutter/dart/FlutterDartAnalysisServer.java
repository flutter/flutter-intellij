/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.dart.server.AnalysisServerListenerAdapter;
import com.google.dart.server.ResponseListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.FlutterInitializer;
import io.flutter.analytics.TimeTracker;
import io.flutter.utils.JsonUtils;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FlutterDartAnalysisServer implements Disposable {
  private static final String FLUTTER_NOTIFICATION_OUTLINE = "flutter.outline";
  private static final String FLUTTER_NOTIFICATION_OUTLINE_KEY = "\"flutter.outline\"";

  @NotNull final Project project;

  /**
   * Each key is a notification identifier.
   * Each value is the set of files subscribed to the notification.
   */
  private final Map<String, List<String>> subscriptions = new HashMap<>();

  @VisibleForTesting
  protected final Map<String, List<FlutterOutlineListener>> fileOutlineListeners = new HashMap<>();

  /**
   * Each key is a request identifier.
   * Each value is the {@link Consumer} for the response.
   */
  private final Map<String, Consumer<JsonObject>> responseConsumers = new HashMap<>();
  private boolean isDisposed = false;

  @NotNull
  public static FlutterDartAnalysisServer getInstance(@NotNull final Project project) {
    return Objects.requireNonNull(project.getService(FlutterDartAnalysisServer.class));
  }

  @NotNull
  private DartAnalysisServerService getAnalysisService() {
    return Objects.requireNonNull(DartPlugin.getInstance().getAnalysisService(project));
  }

  @VisibleForTesting
  public FlutterDartAnalysisServer(@NotNull Project project) {
    this.project = project;
    DartAnalysisServerService analysisService = getAnalysisService();
    analysisService.addResponseListener(new CompatibleResponseListener());
    analysisService.addAnalysisServerListener(new AnalysisServerListenerAdapter() {
      private boolean hasComputedErrors = false;

      @Override
      public void serverConnected(String s) {
        // If the server reconnected we need to let it know that we still care
        // about our subscriptions.
        if (!subscriptions.isEmpty()) {
          sendSubscriptions();
        }
      }

      @Override
      public void computedErrors(String file, List<AnalysisError> errors) {
        if (!hasComputedErrors && project.isOpen()) {
          FlutterInitializer.getAnalytics().sendEventMetric(
            "startup",
            "analysisComputedErrors",
            TimeTracker.getInstance(project).millisSinceProjectOpen()
          );
          hasComputedErrors = true;
        }

        super.computedErrors(file, errors);
      }
    });
    Disposer.register(project, this);
  }

  public void addOutlineListener(@NotNull final String filePath, @NotNull final FlutterOutlineListener listener) {
    synchronized (fileOutlineListeners) {
      final List<FlutterOutlineListener> listeners = fileOutlineListeners.computeIfAbsent(filePath, k -> new ArrayList<>());
      listeners.add(listener);
    }
    addSubscription(FlutterService.OUTLINE, filePath);
  }

  public void removeOutlineListener(@NotNull final String filePath, @NotNull final FlutterOutlineListener listener) {
    final boolean removeSubscription;
    synchronized (fileOutlineListeners) {
      final List<FlutterOutlineListener> listeners = fileOutlineListeners.get(filePath);
      removeSubscription = listeners != null && listeners.remove(listener);
    }
    if (removeSubscription) {
      removeSubscription(FlutterService.OUTLINE, filePath);
    }
  }

  /**
   * Adds a flutter event subscription to the analysis server.
   * <p>
   * Note that <code>filePath</code> must be an absolute path.
   */
  private void addSubscription(@NotNull final String service, @NotNull final String filePath) {
    final List<String> files = subscriptions.computeIfAbsent(service, k -> new ArrayList<>());
    if (!files.contains(filePath)) {
      files.add(filePath);
      sendSubscriptions();
    }
  }

  /**
   * Removes a flutter event subscription from the analysis server.
   * <p>
   * Note that <code>filePath</code> must be an absolute path.
   */
  private void removeSubscription(@NotNull final String service, @NotNull final String filePath) {
    final List<String> files = subscriptions.get(service);
    if (files != null && files.remove(filePath)) {
      sendSubscriptions();
    }
  }

  private void sendSubscriptions() {
    DartAnalysisServerService analysisService = getAnalysisService();
    final String id = analysisService.generateUniqueId();
    analysisService.sendRequest(id, FlutterRequestUtilities.generateAnalysisSetSubscriptions(id, subscriptions));
  }

  @NotNull
  public List<SourceChange> edit_getAssists(@NotNull VirtualFile file, int offset, int length) {
    DartAnalysisServerService analysisService = getAnalysisService();
    return analysisService.edit_getAssists(file, offset, length);
  }

  @Nullable
  public CompletableFuture<List<FlutterWidgetProperty>> getWidgetDescription(@NotNull VirtualFile file, int _offset) {
    final CompletableFuture<List<FlutterWidgetProperty>> result = new CompletableFuture<>();
    final String filePath = FileUtil.toSystemDependentName(file.getPath());
    DartAnalysisServerService analysisService = getAnalysisService();
    final int offset = analysisService.getOriginalOffset(file, _offset);

    final String id = analysisService.generateUniqueId();
    synchronized (responseConsumers) {
      responseConsumers.put(id, (resultObject) -> {
        try {
          final JsonArray propertiesObject = resultObject.getAsJsonArray("properties");
          final ArrayList<FlutterWidgetProperty> properties = new ArrayList<>();
          for (JsonElement propertyObject : propertiesObject) {
            properties.add(FlutterWidgetProperty.fromJson(propertyObject.getAsJsonObject()));
          }
          result.complete(properties);
        }
        catch (Throwable ignored) {
        }
      });
    }

    final JsonObject request = FlutterRequestUtilities.generateFlutterGetWidgetDescription(id, filePath, offset);
    analysisService.sendRequest(id, request);

    return result;
  }


  @Nullable
  public SourceChange setWidgetPropertyValue(int propertyId, FlutterWidgetPropertyValue value) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<SourceChange> result = new AtomicReference<>();
    DartAnalysisServerService analysisService = getAnalysisService();
    final String id = analysisService.generateUniqueId();
    synchronized (responseConsumers) {
      responseConsumers.put(id, (resultObject) -> {
        try {
          final JsonObject propertiesObject = resultObject.getAsJsonObject("change");
          result.set(SourceChange.fromJson(propertiesObject));
        }
        catch (Throwable ignored) {
        }
        latch.countDown();
      });
    }

    final JsonObject request = FlutterRequestUtilities.generateFlutterSetWidgetPropertyValue(id, propertyId, value);
    analysisService.sendRequest(id, request);

    Uninterruptibles.awaitUninterruptibly(latch, 100, TimeUnit.MILLISECONDS);
    return result.get();
  }

  private void processString(String jsonString) {
    if (isDisposed) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      // Short circuit just in case we have been disposed in the time it took
      // for us to get around to listening for the response.
      if (isDisposed) return;
      processResponse(JsonUtils.parseString(jsonString).getAsJsonObject());
    });
  }

  /**
   * Handle the given {@link JsonObject} response.
   */
  private void processResponse(JsonObject response) {
    final JsonElement eventName = response.get("event");
    if (eventName != null && eventName.isJsonPrimitive()) {
      processNotification(response, eventName);
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

    final Consumer<JsonObject> consumer;
    synchronized (responseConsumers) {
      consumer = responseConsumers.remove(idString);
    }
    if (consumer == null) {
      return;
    }

    consumer.consume(resultObject);
  }

  /**
   * Attempts to handle the given {@link JsonObject} as a notification.
   */
  private void processNotification(JsonObject response, @NotNull JsonElement eventName) {
    // If we add code to handle more event types below, update the filter in processString().
    final String event = eventName.getAsString();
    if (event.equals(FLUTTER_NOTIFICATION_OUTLINE)) {
      final JsonObject paramsObject = response.get("params").getAsJsonObject();
      final String file = paramsObject.get("file").getAsString();

      final JsonElement instrumentedCodeElement = paramsObject.get("instrumentedCode");
      final String instrumentedCode = instrumentedCodeElement != null ? instrumentedCodeElement.getAsString() : null;

      final JsonObject outlineObject = paramsObject.get("outline").getAsJsonObject();
      final FlutterOutline outline = FlutterOutline.fromJson(outlineObject);

      final List<FlutterOutlineListener> listenersUpdated;
      synchronized (fileOutlineListeners) {
        final List<FlutterOutlineListener> listeners = fileOutlineListeners.get(file);
        listenersUpdated = listeners != null ? Lists.newArrayList(listeners) : null;
      }
      if (listenersUpdated != null) {
        for (FlutterOutlineListener listener : listenersUpdated) {
          listener.outlineUpdated(file, outline, instrumentedCode);
        }
      }
    }
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

  @Override
  public void dispose() {
    isDisposed = true;
  }
}

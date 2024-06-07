/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonConsumer;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import de.roderick.weberknecht.WebSocketException;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Facilitates sending information to unified analytics.
 */
public class UnifiedAnalytics {
  Boolean enabled = null;
  final Project project;
  final DartToolingDaemonService dtdService;
  @NotNull final FlutterSdkUtil flutterSdkUtil;

  public UnifiedAnalytics(@NotNull Project project) {
    this.project = project;
    this.dtdService = DartToolingDaemonService.getInstance(project);
    this.flutterSdkUtil = new FlutterSdkUtil();
  }

  public void manageConsent() {
    try {
      DartToolingDaemonService service = readyDtdService().get();
      if (service != null) {
        final JsonObject params = new JsonObject();
        params.addProperty("tool", getToolName());

        Boolean shouldShowMessage = shouldShowMessage(service, params).get();
        if (Boolean.TRUE.equals(shouldShowMessage)) {
          String message = getConsentMessage(service, params).get();
          if (message == null) {
            throw new Exception("Unified analytics consent message was null");
          }
          Boolean canSendAnalytics = showMessage(message).get();
          if (canSendAnalytics != null) {
            setTelemetry(service, params, canSendAnalytics);
            clientShowedMessage(service, params);
          }
        }
        // No message needed. Check on whether we should track analytics.
        this.enabled = telemetryEnabled(service, params).get();
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private CompletableFuture<Boolean> telemetryEnabled(DartToolingDaemonService service, JsonObject params) {
    CompletableFuture<Boolean> finalResult = new CompletableFuture<>();
    try {
      service.sendRequest("UnifiedAnalytics.telemetryEnabled", params, false, new DartToolingDaemonConsumer() {
        @Override
        public void received(@NotNull JsonObject object) {
          System.out.println(object);
          JsonObject result = object.getAsJsonObject("result");
          if (result == null) {
            finalResult.completeExceptionally(new Exception("telemetryEnabled JSON result is malformed: " + object));
            return;
          }
          JsonPrimitive value = result.getAsJsonPrimitive("value");
          if (value == null) {
            finalResult.completeExceptionally(new Exception("telemetryEnabled value is null"));
            return;
          }
          finalResult.complete(value.getAsBoolean());
        }
      });
    }
    catch (WebSocketException e) {
      finalResult.completeExceptionally(e);
    }
    return finalResult;
  }

  private CompletableFuture<Boolean> clientShowedMessage(DartToolingDaemonService service, JsonObject params) {
    CompletableFuture<Boolean> finalResult = new CompletableFuture<>();
    try {
      service.sendRequest("UnifiedAnalytics.clientShowedMessage", params, false, new DartToolingDaemonConsumer() {
        @Override
        public void received(@NotNull JsonObject object) {
          System.out.println(object);
          JsonObject result = object.getAsJsonObject("result");
          if (result == null) {
            finalResult.completeExceptionally(new Exception("clientShowedMessage JSON result is malformed: " + object));
            return;
          }
          JsonPrimitive type = result.getAsJsonPrimitive("type");
          if (type == null) {
            finalResult.completeExceptionally(new Exception("setTelemetry type is null"));
            return;
          }
          finalResult.complete("Success".equals(type.getAsString()));
        }
      });
    }
    catch (WebSocketException e) {
      finalResult.completeExceptionally(e);
    }
    return finalResult;
  }

  private CompletableFuture<Boolean> setTelemetry(DartToolingDaemonService service, JsonObject params, Boolean canSendAnalytics) {
    params.addProperty("enable", canSendAnalytics);
    CompletableFuture<Boolean> finalResult = new CompletableFuture<>();
    try {
      service.sendRequest("UnifiedAnalytics.setTelemetry", params, false, new DartToolingDaemonConsumer() {
        @Override
        public void received(@NotNull JsonObject object) {
          System.out.println(object);
          JsonObject result = object.getAsJsonObject("result");
          if (result == null) {
            finalResult.completeExceptionally(new Exception("setTelemetry JSON result is malformed: " + object));
            return;
          }
          JsonPrimitive type = result.getAsJsonPrimitive("type");
          if (type == null) {
            finalResult.completeExceptionally(new Exception("setTelemetry type is null"));
            return;
          }
          finalResult.complete("Success".equals(type.getAsString()));
        }
      });
    }
    catch (WebSocketException e) {
      finalResult.completeExceptionally(e);
    }
    return finalResult;
  }

  private CompletableFuture<Boolean> showMessage(@NotNull String message) {
    CompletableFuture<Boolean> finalResult = new CompletableFuture<>();
    ApplicationManager.getApplication().invokeLater(() -> {
      final Notification notification = new Notification(
        Analytics.GROUP_DISPLAY_ID,
        "Welcome to Flutter!",
        message,
        NotificationType.INFORMATION);
      //noinspection DialogTitleCapitalization
      notification.addAction(new AnAction("Sounds good!") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          notification.expire();
          finalResult.complete(true);
        }
      });
      //noinspection DialogTitleCapitalization
      notification.addAction(new AnAction("No thanks") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          notification.expire();
          finalResult.complete(false);
        }
      });
      Notifications.Bus.notify(notification, project);
      System.out.println("Ran notification");
    });
    return finalResult;
  }

  private String getToolName() throws Exception {
    String ideValue = flutterSdkUtil.getFlutterHostEnvValue();
    if ("IntelliJ-IDEA".equals(ideValue)) {
      return "intellij-plugins";
    }
    else if ("Android-Studio".equals(ideValue)) {
      return "android-studio-plugins";
    }
    else {
      throw new Exception("Tool name cannot be found for IDE type: " + ideValue);
    }
  }

  private CompletableFuture<Boolean> shouldShowMessage(@NotNull DartToolingDaemonService service, @NotNull JsonObject params) {
    CompletableFuture<Boolean> finalResult = new CompletableFuture<>();
    try {
      service.sendRequest("UnifiedAnalytics.shouldShowMessage", params, false, new DartToolingDaemonConsumer() {
        @Override
        public void received(@NotNull JsonObject object) {
          System.out.println(object);
          JsonObject result = object.getAsJsonObject("result");
          if (result == null) {
            finalResult.completeExceptionally(new Exception("Should show message JSON result is malformed: " + object));
            return;
          }
          JsonPrimitive value = result.getAsJsonPrimitive("value");
          if (value == null) {
            finalResult.completeExceptionally(new Exception("Should show message value is null"));
            return;
          }
          finalResult.complete(value.getAsBoolean());
        }
      });
    }
    catch (WebSocketException e) {
      finalResult.completeExceptionally(e);
    }
    return finalResult;
  }

  private CompletableFuture<String> getConsentMessage(@NotNull DartToolingDaemonService service, @NotNull JsonObject params) {
    CompletableFuture<String> finalResult = new CompletableFuture<>();
    try {
      service.sendRequest("UnifiedAnalytics.getConsentMessage", params, false, new DartToolingDaemonConsumer() {
        @Override
        public void received(@NotNull JsonObject object) {
          System.out.println(object);
          JsonObject result = object.getAsJsonObject("result");
          if (result == null) {
            finalResult.completeExceptionally(new Exception("getConsentMessage JSON result is malformed: " + object));
            return;
          }
          JsonPrimitive value = result.getAsJsonPrimitive("value");
          if (value == null) {
            finalResult.completeExceptionally(new Exception("getConsentMessage value is null"));
            return;
          }
          finalResult.complete(value.getAsString());
        }
      });
    }
    catch (WebSocketException e) {
      finalResult.completeExceptionally(e);
    }
    return finalResult;
  }

  private CompletableFuture<DartToolingDaemonService> readyDtdService() {
    CompletableFuture<DartToolingDaemonService> readyService = new CompletableFuture<>();
    int attemptsRemaining = 10;
    final int TIME_IN_BETWEEN = 2;
    while (attemptsRemaining > 0) {
      attemptsRemaining--;
      if (dtdService != null && dtdService.getWebSocketReady()) {
        readyService.complete(dtdService);
        break;
      }
      try {
        Thread.sleep(TIME_IN_BETWEEN * 1000);
      }
      catch (InterruptedException e) {
        readyService.completeExceptionally(e);
        break;
      }
    }
    if (!readyService.isDone()) {
      readyService.completeExceptionally(new Exception("Timed out waiting for DTD websocket to start"));
    }
    return readyService;
  }
}

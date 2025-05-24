/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import de.roderick.weberknecht.WebSocketException;
import io.flutter.dart.DtdUtils;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Facilitates sending information to unified analytics.
 */
public class UnifiedAnalytics {
  private static final @NotNull Logger LOG = Logger.getInstance(UnifiedAnalytics.class);

  @Nullable Boolean enabled = null;
  final Project project;
  final DtdUtils dtdUtils;
  @NotNull final FlutterSdkUtil flutterSdkUtil;


  public UnifiedAnalytics(@NotNull Project project) {
    this.project = project;
    this.dtdUtils = new DtdUtils();
    this.flutterSdkUtil = new FlutterSdkUtil();
  }

  public void manageConsent() {
    try {
      DartToolingDaemonService service = dtdUtils.readyDtdService(project).get();
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
      LOG.info(e);
    }
  }

  private CompletableFuture<JsonObject> makeUnifiedAnalyticsRequest(String requestName,
                                                                    @NotNull DartToolingDaemonService service,
                                                                    @NotNull JsonObject params) {
    CompletableFuture<JsonObject> finalResult = new CompletableFuture<>();
    try {
      service.sendRequest("UnifiedAnalytics." + requestName, params, false, object -> {
        JsonObject result = object.getAsJsonObject("result");
        if (result == null) {
          finalResult.completeExceptionally(new Exception(requestName + " JSON result is malformed: " + object));
          return;
        }
        finalResult.complete(result);
      });
    }
    catch (WebSocketException e) {
      finalResult.completeExceptionally(e);
    }
    return finalResult;
  }

  private @Nullable CompletableFuture<Boolean> telemetryEnabled(@NotNull DartToolingDaemonService service, @NotNull JsonObject params) {
    return makeUnifiedAnalyticsRequest("telemetryEnabled", service, params).thenCompose(result -> {
      assert result != null;
      JsonPrimitive value = result.getAsJsonPrimitive("value");
      CompletableFuture<Boolean> innerResult = new CompletableFuture<>();

      if (value == null) {
        return CompletableFuture.failedFuture(new Exception("telemetryEnabled value is null"));
      }
      innerResult.complete(value.getAsBoolean());
      return innerResult;
    });
  }

  private @Nullable CompletableFuture<Boolean> clientShowedMessage(@NotNull DartToolingDaemonService service, @NotNull JsonObject params) {
    return makeUnifiedAnalyticsRequest("clientShowedMessage", service, params).thenCompose(result -> {
      assert result != null;
      JsonPrimitive type = result.getAsJsonPrimitive("type");
      CompletableFuture<Boolean> innerResult = new CompletableFuture<>();

      if (type == null) {
        return CompletableFuture.failedFuture(new Exception("clientShowedMessage type is null"));
      }
      innerResult.complete("Success".equals(type.getAsString()));
      return innerResult;
    });
  }

  private @Nullable CompletableFuture<Boolean> setTelemetry(@NotNull DartToolingDaemonService service,
                                                            @NotNull JsonObject params,
                                                            Boolean canSendAnalytics) {
    params.addProperty("enable", canSendAnalytics);
    return makeUnifiedAnalyticsRequest("setTelemetry", service, params).thenCompose(result -> {
      assert result != null;
      JsonPrimitive type = result.getAsJsonPrimitive("type");
      CompletableFuture<Boolean> innerResult = new CompletableFuture<>();

      if (type == null) {
        return CompletableFuture.failedFuture(new Exception("setTelemetry type is null"));
      }
      innerResult.complete("Success".equals(type.getAsString()));
      return innerResult;
    });
  }

  private CompletableFuture<Boolean> showMessage(@NotNull String message) {
    CompletableFuture<Boolean> finalResult = new CompletableFuture<>();
    OpenApiUtils.safeInvokeLater(() -> {
      final Notification notification = new Notification(
        "Flutter Usage Statistics", // Analytics.GROUP_DISPLAY_ID,
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

  private @Nullable CompletableFuture<Boolean> shouldShowMessage(@NotNull DartToolingDaemonService service, @NotNull JsonObject params) {
    return makeUnifiedAnalyticsRequest("shouldShowMessage", service, params).thenCompose(result -> {
      assert result != null;
      JsonPrimitive value = result.getAsJsonPrimitive("value");
      CompletableFuture<Boolean> innerResult = new CompletableFuture<>();

      if (value == null) {
        return CompletableFuture.failedFuture(new Exception("shouldShowMessage value is null"));
      }
      innerResult.complete(value.getAsBoolean());
      return innerResult;
    });
  }

  private @Nullable CompletableFuture<String> getConsentMessage(@NotNull DartToolingDaemonService service, @NotNull JsonObject params) {
    return makeUnifiedAnalyticsRequest("getConsentMessage", service, params).thenCompose(result -> {
      assert result != null;
      JsonPrimitive value = result.getAsJsonPrimitive("value");
      CompletableFuture<String> innerResult = new CompletableFuture<>();
      if (value == null) {
        return CompletableFuture.failedFuture(new Exception("getConsentMessage value is null"));
      }
      innerResult.complete(value.getAsString());
      return innerResult;
    });
  }
}

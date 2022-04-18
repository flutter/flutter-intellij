/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.concurrency.QueueProcessor;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight Google Analytics integration.
 */
public class Analytics {
  public static final String GROUP_DISPLAY_ID = "Flutter Usage Statistics";

  private static final String analyticsUrl = "https://www.google-analytics.com/collect";
  private static final String applicationName = "Flutter IntelliJ Plugin";
  private static final String trackingId = "UA-67589403-7";

  private static final int maxExceptionLength = 512;

  @NotNull
  private final String clientId;
  @NotNull
  private final String pluginVersion;
  @NotNull
  private final String platformName;
  @NotNull
  private final String platformVersion;

  private Transport transport = new HttpTransport();
  private final ThrottlingBucket bucket = new ThrottlingBucket(20);
  private boolean myCanSend = false;

  public Analytics(@NotNull String clientId, @NotNull String pluginVersion, @NotNull String platformName, @NotNull String platformVersion) {
    this.clientId = clientId;
    this.pluginVersion = pluginVersion;
    this.platformName = platformName;
    this.platformVersion = platformVersion;
  }

  @NotNull
  public String getClientId() {
    return clientId;
  }

  public boolean canSend() {
    return myCanSend;
  }

  public void setCanSend(boolean value) {
    this.myCanSend = value;
  }

  /**
   * Public for testing.
   */
  public void setTransport(@NotNull Transport transport) {
    this.transport = transport;
  }

  public void sendScreenView(@NotNull String viewName) {
    final Map<String, String> args = new HashMap<>();
    args.put("cd", viewName);
    sendPayload("screenview", args, null);
  }

  public void sendEvent(@NotNull String category, @NotNull String action) {
    sendEvent(category, action, null);
  }

  public void sendEvent(@NotNull String category, @NotNull String action, @Nullable FlutterSdk flutterSdk) {
    final Map<String, String> args = new HashMap<>();
    args.put("ec", category);
    args.put("ea", action);
    sendPayload("event", args, flutterSdk);
  }

  public void sendEventWithSdk(@NotNull String category, @NotNull String action, @NotNull String sdkVersion) {
    final Map<String, String> args = new HashMap<>();
    args.put("ec", category);
    args.put("ea", action);
    sendPayload("event", args, null, sdkVersion);
  }

  public void sendEventMetric(@NotNull String category, @NotNull String action, int value) {
    sendEventMetric(category, action, value, null);
  }

  public void sendEventMetric(@NotNull String category, @NotNull String action, int value, @Nullable FlutterSdk flutterSdk) {
    final Map<String, String> args = new HashMap<>();
    args.put("ec", category);
    args.put("ea", action);
    args.put("ev", Integer.toString(value));
    sendPayload("event", args, flutterSdk);
  }

  public void sendEvent(@NotNull String category, @NotNull String action, @NotNull String label, @NotNull String value) {
    final Map<String, String> args = new HashMap<>();
    args.put("ec", category);
    args.put("ea", action);
    if (!label.isEmpty()) {
      args.put("el", label);
    }
    args.put("ev", value);
    sendPayload("event", args, null);
  }

  public void sendTiming(@NotNull String category, @NotNull String variable, long timeMillis) {
    final Map<String, String> args = new HashMap<>();
    args.put("utc", category);
    args.put("utv", variable);
    args.put("utt", Long.toString(timeMillis));
    sendPayload("timing", args, null);
  }

  public void sendExpectedException(@NotNull String location, @NotNull Throwable throwable) {
    sendEvent("expected-exception", location + ":" + throwable.getClass().getName());
  }

  public void sendException(@NotNull String throwableText, boolean isFatal) {
    String description = throwableText;
    description = description.replaceAll("com.intellij.openapi.", "c.i.o.");
    description = description.replaceAll("com.intellij.", "c.i.");
    if (description.length() > maxExceptionLength) {
      description = description.substring(0, maxExceptionLength);
    }

    final Map<String, String> args = new HashMap<>();
    args.put("exd", description);
    if (isFatal) {
      args.put("'exf'", "1");
    }
    sendPayload("exception", args, null);
  }

  private void sendPayload(@NotNull String hitType, @NotNull Map<String, String> args, @Nullable FlutterSdk flutterSdk) {
    sendPayload(hitType, args, flutterSdk, null);
  }

  private void sendPayload(@NotNull String hitType, @NotNull Map<String, String> args, @Nullable FlutterSdk flutterSdk, @Nullable String sdkVersion) {
    if (!canSend()) {
      return;
    }

    if (!bucket.removeDrop()) {
      return;
    }

    args.put("v", "1"); // protocol version
    args.put("ds", "app"); // specify an 'app' data source

    args.put("an", applicationName);
    args.put("av", pluginVersion);

    args.put("aiid", platformName); // Record the platform name as the application installer ID
    args.put("cd1", platformVersion); // Record the Open API version as a custom dimension

    // If the Flutter SDK is provided, send the SDK version in a custom dimension.
    if (flutterSdk != null) {
      final FlutterSdkVersion flutterVersion = flutterSdk.getVersion();
      if (flutterVersion.getVersionText() != null) {
        args.put("cd2", flutterVersion.getVersionText());
      }
    } else if (sdkVersion != null) {
      args.put("cd2", sdkVersion);
    }

    // Record whether this client uses bazel.
    if (anyProjectUsesBazel()) {
      args.put("cd3", "bazel");
    }

    args.put("tid", trackingId);
    args.put("cid", clientId);
    args.put("t", hitType);

    try {
      final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      args.put("sr", screenSize.width + "x" + screenSize.height);
    }
    catch (HeadlessException he) {
      // ignore this - allow the tests to run when the IDE is headless
    }

    final String language = System.getProperty("user.language");
    if (language != null) {
      args.put("ul", language);
    }

    transport.send(analyticsUrl, args);
  }

  /**
   * Return true if any open project is a Dart project and uses Bazel.
   */
  private boolean anyProjectUsesBazel() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isDisposed()) {
        continue;
      }

      if (DartSdk.getDartSdk(project) != null && WorkspaceCache.getInstance(project).isBazel()) {
        return true;
      }
    }

    return false;
  }

  public interface Transport {
    void send(String url, Map<String, String> values);
  }

  private static class HttpTransport implements Transport {
    private final QueueProcessor<Runnable> sendingQueue = QueueProcessor.createRunnableQueueProcessor();

    @Nullable
    private static String createUserAgent() {
      final String locale = Locale.getDefault().toString();

      if (SystemInfo.isWindows) {
        return "Mozilla/5.0 (Windows; Windows; Windows; " + locale + ")";
      }
      else if (SystemInfo.isMac) {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X; Macintosh; " + locale + ")";
      }
      else if (SystemInfo.isLinux) {
        return "Mozilla/5.0 (Linux; Linux; Linux; " + locale + ")";
      }

      return null;
    }

    @Override
    public void send(String url, Map<String, String> values) {
      sendingQueue.add(() -> {
        try {
          final StringBuilder postData = new StringBuilder();
          for (Map.Entry<String, String> param : values.entrySet()) {
            if (postData.length() != 0) {
              postData.append('&');
            }
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(param.getValue(), "UTF-8"));
          }
          final byte[] postDataBytes = postData.toString().getBytes(StandardCharsets.UTF_8);
          final HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
          conn.setRequestMethod("POST");
          conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
          conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
          final String userAgent = createUserAgent();
          if (userAgent != null) {
            conn.setRequestProperty("User-Agent", createUserAgent());
          }
          conn.setDoOutput(true);
          conn.getOutputStream().write(postDataBytes);

          final InputStream in = conn.getInputStream();
          //noinspection ResultOfMethodCallIgnored
          in.read();
          in.close();
        }
        catch (IOException ignore) {
        }
      });
    }
  }
}
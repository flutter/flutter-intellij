/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.widgetpreviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import io.flutter.logging.PluginLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WidgetPreviewListener implements ProcessListener {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(WidgetPreviewListener.class);
  @NotNull private final CompletableFuture<String> urlFuture;
  boolean isVerboseMode;
  @NotNull private final Consumer<String> onError;
  @NotNull private final Consumer<String> onSuccess;

  public WidgetPreviewListener(@NotNull CompletableFuture<String> urlFuture, boolean isVerboseMode, @NotNull Consumer<String> onError,
                               Consumer<@NotNull String> onSuccess) {
    this.urlFuture = urlFuture;
    this.isVerboseMode = isVerboseMode;
    this.onError = onError;
    this.onSuccess = onSuccess;
  }

  private static final Pattern URL_PATTERN = Pattern.compile("http://localhost:\\d+/?");

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    if (outputType != ProcessOutputTypes.STDOUT) {
      return;
    }

    String text = event.getText();
    if (text == null) return;
    LOG.debug("STDOUT from Widget previewer: " + text);

    // Don't parse further if the URL has already been found.
    if (urlFuture.isDone()) {
      return;
    }

    // If we are in verbose mode, the text will have a prepended section for timings, e.g.:
    // ```
    // [   +4 ms] [{"event":"widget_preview.logMessage",...}]
    if (isVerboseMode) {
      text = jsonFromVerboseOutput(text);
    }

    try {
      final String maybeUrl = tryToExtractUrlFromJson(text);
      if (maybeUrl != null && !urlFuture.isDone()) {
        urlFuture.complete(maybeUrl);
      }
    }
    catch (JsonSyntaxException e) {
      // This might happen if the output is not JSON. We can fallback to regex.
      final Matcher matcher = URL_PATTERN.matcher(text);
      if (matcher.find()) {
        final String url = matcher.group();
        if (!urlFuture.isDone()) {
          urlFuture.complete(url);
        }
      }
    }

    urlFuture.whenComplete((url, ex) -> {
      if (ex != null) {
        LOG.error("Error getting widget preview URL", ex);
        final String message = ex.getMessage();
        this.onError.accept(message);
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        if (url == null) {
          this.onError.accept("No URL found");
          return;
        }

        this.onSuccess.accept(url);
      });
    });
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    if (!urlFuture.isDone()) {
      urlFuture.completeExceptionally(new Exception("Process terminated before URL was found. Exit code: " + event.getExitCode()));
    }
  }

  private static @NotNull String jsonFromVerboseOutput(@NotNull String text) {
    if (text.startsWith("[") && !text.startsWith("[{")) {
      final int closingBracketLocation = text.indexOf("]");
      if (closingBracketLocation != -1) {
        text = text.substring(closingBracketLocation + 1).trim();
      }
    }
    return text;
  }

  private static @Nullable String tryToExtractUrlFromJson(@NotNull String text) {
    final JsonElement element = JsonParser.parseString(text);
    if (element == null) return null;
    if (element.isJsonArray()) {
      final JsonArray jsonArray = element.getAsJsonArray();
      if (jsonArray == null) return null;
      for (JsonElement item : jsonArray) {
        if (item == null) continue;
        if (item.isJsonObject()) {
          final JsonObject obj = item.getAsJsonObject();
          if (obj == null) continue;
          if (obj.has("event") && obj.has("params")) {
            JsonElement eventElement = obj.get("event");
            if (eventElement == null) continue;
            final String eventName = eventElement.getAsString();
            if ("widget_preview.app.webLaunchUrl".equals(eventName) || "widget_preview.started".equals(eventName)) {
              JsonElement eventParams = obj.get("params");
              if (eventParams == null) continue;
              final JsonObject params = eventParams.getAsJsonObject();
              if (params == null) continue;
              if (params.has("url")) {
                JsonElement urlElement = params.get("url");
                if (urlElement == null) continue;
                return urlElement.getAsString();
              }
            }
          }
        }
      }
    }
    return null;
  }
}

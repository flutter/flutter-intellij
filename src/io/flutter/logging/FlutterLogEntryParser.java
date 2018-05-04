/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import static io.flutter.logging.FlutterLog.LOGGING_STREAM_ID;

public class FlutterLogEntryParser {

  // Known entry categories.
  public static final String LOG_CATEGORY = "flutter.log";
  public static final String DAEMON_CATEGORY = "flutter.daemon";
  public static final String STDIO_STDOUT_CATEGORY = "stdio.stdout";

  @Nullable
  public static FlutterLogEntry parse(@Nullable String id, @Nullable Event event) {
    if (id != null && event != null) {
      switch (id) {
        case LOGGING_STREAM_ID:
          return parseLoggingEvent(event);
      }
    }

    return null;
  }

  @Nullable
  static FlutterLogEntry parseDaemonEvent(@NotNull ProcessEvent event, @Nullable Key outputType) {
    // TODO(pq): process outputType
    final String text = event.getText();
    if (text.isEmpty()) return null;

    return parseDaemonEvent(text);
  }

  @VisibleForTesting
  @Nullable
  public static FlutterLogEntry parseDaemonEvent(@NotNull String eventText) {
    // Make a working copy.
    String text = eventText;

    // TODO(pq): replace heuristic parsing for JSON w/ something more robust.
    if (text.startsWith("[")) {
      // Remove bracketing braces.
      text = text.substring(1, text.length() - 2);
    }

    try {
      // TODO(pq): there's lots to parse; for now, just extract a message value.
      final JsonObject json = new JsonParser().parse(text).getAsJsonObject();
      final JsonObject params = json.get("params").getAsJsonObject();

      if (params.has("message")) {
        return new FlutterLogEntry(timestamp(), DAEMON_CATEGORY, params.get("message").getAsJsonPrimitive().getAsString());
      }

      if (json.has("event")) {
        final JsonElement eventElement = json.get("event");
        return new FlutterLogEntry(timestamp(), DAEMON_CATEGORY, eventElement.getAsJsonPrimitive().getAsString());
      }
    }
    catch (Throwable e) {
      // TODO(pq): for now, text that does not parse to JSON is categorized simply as STDOUT; this will change.
      return new FlutterLogEntry(timestamp(), STDIO_STDOUT_CATEGORY, eventText);
    }

    return null;
  }

  private static FlutterLogEntry parseLoggingEvent(@NotNull Event event) {
    // TODO(pq): parse event timestamp?
    // TODO(pq): parse more robustly; consider more properties.
    final JsonObject json = event.getJson();
    final JsonObject logRecord = json.get("logRecord").getAsJsonObject();
    final JsonObject message = logRecord.getAsJsonObject().get("message").getAsJsonObject();
    final String messageContents = message.get("valueAsString").getAsJsonPrimitive().toString();
    return new FlutterLogEntry(timestamp(), LOG_CATEGORY, messageContents);
  }

  private static String timestamp() {
    return new SimpleDateFormat("HH:mm:ss.SSS").format(Calendar.getInstance().getTime());
  }

}

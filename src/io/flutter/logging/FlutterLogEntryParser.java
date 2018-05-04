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
import io.flutter.perf.HeapMonitor;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.IsolateRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Calendar;

import static io.flutter.logging.FlutterLog.LOGGING_STREAM_ID;

public class FlutterLogEntryParser {
  private static final NumberFormat nf = new DecimalFormat();
  private static final NumberFormat df1 = new DecimalFormat();

  static {
    df1.setMaximumFractionDigits(1);
  }

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
        case VmService.GC_STREAM_ID:
          final IsolateRef isolateRef = event.getIsolate();
          final HeapMonitor.HeapSpace newHeapSpace = new HeapMonitor.HeapSpace(event.getJson().getAsJsonObject("new"));
          final HeapMonitor.HeapSpace oldHeapSpace = new HeapMonitor.HeapSpace(event.getJson().getAsJsonObject("old"));

          // TODO(devoncarew): Update the VM library - timestamp is a long.
          final long timestamp;

          if (event.getJson().has("timestamp")) {
            timestamp = event.getJson().get("timestamp").getAsLong();
          }
          else {
            timestamp = System.currentTimeMillis();
          }

          final double time = newHeapSpace.getTime() + oldHeapSpace.getTime();
          final int used = newHeapSpace.getUsed() + newHeapSpace.getExternal()
                           + oldHeapSpace.getUsed() + oldHeapSpace.getExternal();
          final int maxHeap = newHeapSpace.getCapacity() + oldHeapSpace.getCapacity();

          final long timeMs = Math.round(time * 1000);
          final double usedMB = used / (1024.0 * 1024.0);
          final double maxMB = maxHeap / (1024.0 * 1024.0);

          return new FlutterLogEntry(
            timestamp,
            "runtime.gc", isolateRef.getId() + " • collection time " +
                          nf.format(timeMs) + "ms • " +
                          df1.format(usedMB) + "MB used of " + df1.format(maxMB) + "MB");
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

  private static long timestamp() {
    return Calendar.getInstance().getTimeInMillis();
  }
}

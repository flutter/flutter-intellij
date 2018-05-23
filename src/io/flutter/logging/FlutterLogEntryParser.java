/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;
import io.flutter.perf.HeapMonitor;
import io.flutter.run.daemon.DaemonApi;
import io.flutter.utils.StdoutJsonParser;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.IsolateRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import static io.flutter.logging.FlutterLog.LOGGING_STREAM_ID;

public class FlutterLogEntryParser {
  private static final NumberFormat nf = new DecimalFormat();
  private static final NumberFormat df1 = new DecimalFormat();

  private final StdoutJsonParser stdoutParser = new StdoutJsonParser();

  static {
    df1.setMinimumFractionDigits(1);
    df1.setMaximumFractionDigits(1);
  }

  // Known entry categories.
  public static final String LOG_CATEGORY = "flutter.log";
  public static final String TOOLS_CATEGORY = "flutter.tools";
  public static final String STDIO_STDOUT_CATEGORY = "stdout";

  @Nullable
  public FlutterLogEntry parse(@Nullable String id, @Nullable Event event) {
    if (id != null && event != null) {
      switch (id) {
        case LOGGING_STREAM_ID:
          return parseLoggingEvent(event);
        case VmService.GC_STREAM_ID:
          return parseGCEvent(event);
      }
    }

    return null;
  }

  @Nullable
  FlutterLogEntry parseDaemonEvent(@NotNull ProcessEvent event, @Nullable Key outputType) {
    // TODO(pq): process outputType
    final String text = event.getText();
    if (text.isEmpty()) return null;

    return parseDaemonEvent(text);
  }

  @VisibleForTesting
  @Nullable
  public FlutterLogEntry parseDaemonEvent(@NotNull String eventText) {
    // TODO(pq): restructure parsing to ensure DaemonEvent messages are only parsed once
    // (with daemon JSON going into one stream and regular log messages going elsewhere)
    stdoutParser.appendOutput(eventText);
    for (String line : stdoutParser.getAvailableLines()) {
      //noinspection StatementWithEmptyBody
      if (DaemonApi.parseAndValidateDaemonEvent(line.trim()) != null) {
        // Skip.
      }
      else {
        return new FlutterLogEntry(timestamp(), TOOLS_CATEGORY, line);
      }
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
    return new FlutterLogEntry(timestamp(event), LOG_CATEGORY, messageContents);
  }

  @NotNull
  private static FlutterLogEntry parseGCEvent(@NotNull Event event) {
    final IsolateRef isolateRef = event.getIsolate();
    final HeapMonitor.HeapSpace newHeapSpace = new HeapMonitor.HeapSpace(event.getJson().getAsJsonObject("new"));
    final HeapMonitor.HeapSpace oldHeapSpace = new HeapMonitor.HeapSpace(event.getJson().getAsJsonObject("old"));

    final double time = newHeapSpace.getTime() + oldHeapSpace.getTime();
    final int used = newHeapSpace.getUsed() + newHeapSpace.getExternal()
                     + oldHeapSpace.getUsed() + oldHeapSpace.getExternal();
    final int maxHeap = newHeapSpace.getCapacity() + oldHeapSpace.getCapacity();

    final long timeMs = Math.round(time * 1000);
    final double usedMB = used / (1024.0 * 1024.0);
    final double maxMB = maxHeap / (1024.0 * 1024.0);

    return new FlutterLogEntry(
      timestamp(event),
      "runtime.gc", "collection time " +
                    nf.format(timeMs) + "ms • " +
                    df1.format(usedMB) + "MB used of " + df1.format(maxMB) + "MB • " +
                    isolateRef.getId());
  }

  private static long timestamp() {
    return System.currentTimeMillis();
  }

  private static long timestamp(Event event) {
    // TODO(devoncarew): Update the VM library - timestamp is a long.
    if (event.getJson().has("timestamp")) {
      return event.getJson().get("timestamp").getAsLong();
    }
    else {
      return timestamp();
    }
  }

  public FlutterLogEntry parseConsoleEvent(String text, ConsoleViewContentType type) {
    if (type == ConsoleViewContentType.NORMAL_OUTPUT) {
      return parseDaemonEvent(text);
    }
    // TODO(pq): handle else (errors, etc).
    return null;
  }
}

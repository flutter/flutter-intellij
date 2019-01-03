/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.jetbrains.lang.dart.ide.runner.DartConsoleFilter;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.logging.util.LineInfo;
import io.flutter.logging.util.LineParser;
import io.flutter.logging.util.StyledText;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.daemon.DaemonApi;
import io.flutter.server.vmService.HeapMonitor;
import io.flutter.utils.StdoutJsonParser;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.flutter.logging.FlutterLog.LOGGING_STREAM_ID;
import static io.flutter.logging.FlutterLogEntry.Kind;
import static io.flutter.logging.FlutterLogEntry.UNDEFINED_LEVEL;

public class FlutterLogEntryParser {
  // Known entry categories.
  public static final String GC_CATEGORY = "runtime.gc";
  public static final String LOG_CATEGORY = "flutter.log";
  public static final String TOOLS_CATEGORY = "flutter.tools";
  public static final String STDIO_STDOUT_CATEGORY = "stdout";

  public static int GC_EVENT_LEVEL = FlutterLog.Level.FINER.value;

  private static final NumberFormat nf = new DecimalFormat();
  private static final NumberFormat df1 = new DecimalFormat();

  static {
    df1.setMinimumFractionDigits(1);
    df1.setMaximumFractionDigits(1);
  }

  private FlutterDebugProcess debugProcess;

  private final LineHandler lineHandler;

  public FlutterLogEntryParser(@NotNull Project project, @Nullable Module module) {
    lineHandler = new LineHandler(project, module);
  }

  public void setDebugProcess(FlutterDebugProcess debugProcess) {
    this.debugProcess = debugProcess;
  }

  public FlutterDebugProcess getDebugProcess() {
    return debugProcess;
  }

  private final StdoutJsonParser stdoutParser = new StdoutJsonParser();

  private FlutterLogEntry parseLoggingEvent(@NotNull Event event) {
    // TODO(pq): parse more robustly; consider more properties (error, stackTrace)
    final JsonObject json = event.getJson();
    final JsonObject logRecord = json.get("logRecord").getAsJsonObject();
    final Instance message = new Instance(logRecord.getAsJsonObject().get("message").getAsJsonObject());

    String category = LOG_CATEGORY;
    final JsonObject loggerName = logRecord.getAsJsonObject().get("loggerName").getAsJsonObject();
    if (loggerName != null) {
      final String str = new Instance(loggerName).getValueAsString();
      if (str != null && !str.isEmpty()) {
        category = str;
      }
    }

    int level = UNDEFINED_LEVEL.value;
    final JsonElement levelElement = logRecord.getAsJsonObject().get("level");
    if (levelElement instanceof JsonPrimitive) {
      final int setLevel = levelElement.getAsInt();
      // only set level if defined
      if (setLevel > 0) {
        level = setLevel;
      }
    }

    // TODO(pq): If message.getValueAsStringIsTruncated() is true, we'll need to retrieve the full string
    // value and update this entry after creation.
    String messageStr = message.getValueAsString();
    if (message.getValueAsStringIsTruncated()) {
      messageStr += "...";
    }

    final LineInfo lineInfo = parseLine(messageStr, category);
    final FlutterLogEntry entry =  new FlutterLogEntry(timestamp(), lineInfo, level);

    final Instance data = new Instance(logRecord.getAsJsonObject().get("error").getAsJsonObject());
    if (!data.getValueAsStringIsTruncated()) {
      entry.setData(data.getValueAsString());
    }
    else {
      final Isolate isolate = new Isolate(json.get("isolate").getAsJsonObject());
      debugProcess.getVmServiceWrapper().getObject(isolate.getId(), data.getId(), new GetObjectConsumer() {
        @Override
        public void received(Obj response) {
          entry.setData(((Instance)response).getValueAsString());
        }

        @Override
        public void received(Sentinel response) {
          entry.setData(null);
        }

        @Override
        public void onError(RPCError error) {
          // TODO(pq): log?
        }
      });
    }

    return entry;
  }

  @NotNull
  private FlutterLogEntry parseGCEvent(@NotNull Event event) {
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
    final String message = "collection time " + nf.format(timeMs) + "ms • " +  df1.format(usedMB) + "MB used of " + df1.format(maxMB) + "MB • " + isolateRef.getId();

    final LineInfo lineInfo = parseLine(message, GC_CATEGORY);
    return new FlutterLogEntry(timestamp(), lineInfo, UNDEFINED_LEVEL.value);
  }

  private static Kind parseKind(@NotNull String message, @NotNull String category) {
    if (category.equals(TOOLS_CATEGORY)) {
      message = message.trim();
      if (message.equals("Performing hot reload...") || message.equals("Initializing hot reload...")) {
        return Kind.RELOAD;
      }
      if (message.equals("Performing hot restart...") || message.equals("Initializing hot restart...")) {
        return Kind.RESTART;
      }
      // TODO(pq): remove string matching in favor of some kind of tag coming from the framework.
      if (message.startsWith("══╡ EXCEPTION CAUGHT BY WIDGETS LIBRARY")) {
        return Kind.WIDGET_ERROR_START;
      }
    }
    return Kind.UNSPECIFIED;
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

  static class LineHandler extends LineParser {
    final List<StyledText> parsed = new ArrayList<>();

    LineHandler(@NotNull Project project, @Nullable Module module) {
      super(createMessageFilters(project, module));
    }

    @Override
    public void write(@NotNull StyledText styledText) {
      parsed.add(styledText);
    }

    List<StyledText> parseLine(@NotNull String line) {
      parse(line);
      // Copy results and clear.
      final ArrayList<StyledText> results = new ArrayList<>(parsed);
      parsed.clear();
      return results;
    }

    @NotNull
    static List<Filter> createMessageFilters(@NotNull Project project, @Nullable Module module) {
      final List<Filter> filters = new ArrayList<>();
      if (module != null) {
        filters.add(new FlutterConsoleFilter(module));
      }
      filters.addAll(Arrays.asList(
        new DartConsoleFilter(project, project.getBaseDir()),
        new UrlFilter()
      ));
      return filters;
    }
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
        // Trim "flutter: " prefix (made redundant by category).
        if (line.startsWith("flutter:")) {
          line = line.substring(8);
        }
        // Fix unicode escape codes.
        line = line.replaceAll("\\\\\\^\\[", "\u001b");

        final LineInfo lineInfo = parseLine(line, TOOLS_CATEGORY);
        return new FlutterLogEntry(timestamp(), lineInfo, UNDEFINED_LEVEL.value);
      }
    }

    return null;
  }


  LineInfo parseLine(@NotNull String line, @NotNull String category) {
    final Kind kind = parseKind(line, category);
    // On reloads / restarts, clear cached styles in case we're in the middle of an unterminated style block.
    if (kind == FlutterLogEntry.Kind.RELOAD || kind == FlutterLogEntry.Kind.RESTART) {
      lineHandler.clear();
    }

    final List<StyledText> styledText = lineHandler.parseLine(line);
    return new LineInfo(line, styledText, kind, category);
  }

  public FlutterLogEntry parseConsoleEvent(String text, ConsoleViewContentType type) {
    if (type == ConsoleViewContentType.NORMAL_OUTPUT) {
      return parseDaemonEvent(text);
    }
    // TODO(pq): handle else (errors, etc).
    return null;
  }
}

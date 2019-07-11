/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;
import com.jetbrains.lang.dart.ide.runner.DartConsoleFilter;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.logging.text.LineInfo;
import io.flutter.logging.text.LineParser;
import io.flutter.logging.text.StyledText;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.daemon.DaemonApi;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StdoutJsonParser;
import io.flutter.vmService.HeapMonitor;
import io.flutter.vmService.VMServiceManager;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static io.flutter.logging.FlutterLogEntry.Kind;
import static io.flutter.logging.FlutterLogEntry.UNDEFINED_LEVEL;

public class FlutterLogEntryParser {
  // Known entry categories.
  public static final String ERROR_CATEGORY = "flutter.error";
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

  private static final Logger LOG = Logger.getInstance(FlutterLogEntryParser.class);

  private final EventDispatcher<FlutterLogEntry.ContentListener>
    contentChangedDispatcher = EventDispatcher.create(FlutterLogEntry.ContentListener.class);
  private FlutterApp app;

  public void addListener(@NotNull FlutterLogEntry.ContentListener listener, @NotNull Disposable parent) {
    contentChangedDispatcher.addListener(listener, parent);
  }

  static abstract class GetObjectAdapter implements GetObjectConsumer {
    @Override
    public abstract void received(Obj response);

    @Override
    public void received(Sentinel response) {
      // No-op.
    }

    @Override
    public void onError(RPCError error) {
      // No-op.
    }
  }

  private final LineHandler lineHandler;
  private CompletableFuture<InspectorService.ObjectGroup> inspectorObjectGroup;

  public FlutterLogEntryParser(@NotNull Project project, @Nullable Module module) {
    lineHandler = new LineHandler(createMessageFilters(project, module), null);
  }

  public FlutterDebugProcess getDebugProcess() {
    return app != null ? app.getFlutterDebugProcess() : null;
  }

  private final StdoutJsonParser stdoutParser = new StdoutJsonParser();

  private List<FlutterLogEntry> parseLoggingEvent(@NotNull Event event) {
    final LogRecord logRecord = event.getLogRecord();
    final InstanceRef message = logRecord.getMessage();

    String category = LOG_CATEGORY;
    final InstanceRef loggerName = logRecord.getLoggerName();
    if (!loggerName.getValueAsString().isEmpty()) {
      category = loggerName.getValueAsString();
    }

    final int level = logRecord.getLevel() == -1 ? UNDEFINED_LEVEL.value : logRecord.getLevel();

    final FlutterDebugProcess debugProcess = getDebugProcess();

    final String messageStr = message.getValueAsString();
    final FlutterLogEntry entry = lineHandler.parseEntry(messageStr, category, level);

    if (message.getValueAsStringIsTruncated()) {
      entry.setMessage(messageStr + "...");
      if (debugProcess != null) {
        final IsolateRef isolateRef = event.getIsolate();
        debugProcess.getVmServiceWrapper().getObject(isolateRef.getId(), message.getId(), new GetObjectAdapter() {
          @Override
          public void received(Obj response) {
            entry.setMessage(((Instance)response).getValueAsString());
            contentChangedDispatcher.getMulticaster().onContentUpdate();
          }
        });
      }
    }

    final InstanceRef data = logRecord.getError();
    if (!data.getValueAsStringIsTruncated()) {
      entry.setData(data.getValueAsString());
    }
    else {
      if (debugProcess != null) {
        final IsolateRef isolateRef = event.getIsolate();
        debugProcess.getVmServiceWrapper().getObject(isolateRef.getId(), data.getId(), new GetObjectAdapter() {
          @Override
          public void received(Obj response) {
            entry.setData(((Instance)response).getValueAsString());
            contentChangedDispatcher.getMulticaster().onContentUpdate();
          }

          @Override
          public void received(Sentinel response) {
            entry.setData(null);
            contentChangedDispatcher.getMulticaster().onContentUpdate();
          }
        });
      }
    }

    return Collections.singletonList(entry);
  }

  @NotNull
  private List<FlutterLogEntry> parseGCEvent(@NotNull Event event) {
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
    final String message = "collection time " +
                           nf.format(timeMs) +
                           "ms • " +
                           df1.format(usedMB) +
                           "MB used of " +
                           df1.format(maxMB) +
                           "MB • " +
                           isolateRef.getId();

    return Collections.singletonList(lineHandler.parseEntry(message, GC_CATEGORY, UNDEFINED_LEVEL.value));
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
      // TODO(pq): remove string matching once all widget errors are coming as "Flutter.Error" exension events.
      if (message.startsWith("══╡ EXCEPTION CAUGHT BY WIDGETS LIBRARY")) {
        return Kind.FLUTTER_ERROR;
      }
    }
    return Kind.UNSPECIFIED;
  }

  private static long timestamp(Event event) {
    return event.getTimestamp() == -1 ? System.currentTimeMillis() : event.getTimestamp();
  }

  @Nullable
  public List<FlutterLogEntry> parse(@Nullable String streamId, @Nullable Event event) {
    if (streamId != null && event != null) {
      switch (streamId) {
        case VmService.LOGGING_STREAM_ID:
        case VMServiceManager.LOGGING_STREAM_ID_OLD:
          return parseLoggingEvent(event);

        case VmService.GC_STREAM_ID:
          return parseGCEvent(event);

        case VmService.EXTENSION_STREAM_ID:
          return parseExtensionEvent(event);
      }
    }

    return null;
  }

  private List<FlutterLogEntry> parseExtensionEvent(@NotNull Event event) {
    final String extensionKind = event.getExtensionKind();
    if (Objects.equals(extensionKind, "Flutter.Error")) {
      return parseFlutterError(event);
    }
    return null;
  }

  private List<FlutterLogEntry> parseFlutterError(@NotNull Event event) {
    final List<FlutterLogEntry> entries = new ArrayList<>();
    final ExtensionData extensionData = event.getExtensionData();
    final DiagnosticsNode diagnosticsNode = parseDiagnosticsNode(extensionData.getJson().getAsJsonObject());
    final String description = diagnosticsNode.toString();
    final FlutterLogEntry entry = lineHandler.parseEntry(description, ERROR_CATEGORY, FlutterLog.Level.SEVERE.value);
    entry.setKind(Kind.FLUTTER_ERROR);
    entry.setData(diagnosticsNode);
    entries.add(entry);
    return entries;
  }

  @NotNull
  private DiagnosticsNode parseDiagnosticsNode(@NotNull JsonObject json) {
    assert (inspectorObjectGroup != null);
    assert (app != null);
    return new DiagnosticsNode(json, inspectorObjectGroup, app, false, null);
  }

  @Nullable
  FlutterLogEntry parseDaemonEvent(@NotNull ProcessEvent event, @Nullable Key outputType) {
    // TODO(pq): process outputType
    final String text = event.getText();
    if (text.isEmpty()) return null;

    return parseDaemonEvent(text);
  }

  public static class LineHandler extends LineParser {
    final List<StyledText> parsed = new ArrayList<>();

    public LineHandler(@NotNull List<Filter> filters, @Nullable SimpleTextAttributes initialStyle) {
      super(filters);
      this.style = initialStyle;
    }

    @Override
    public void write(@NotNull StyledText styledText) {
      parsed.add(styledText);
    }

    public List<StyledText> parseLineStyle(@NotNull String line) {
      parse(line);
      // Copy results and clear.
      final ArrayList<StyledText> results = new ArrayList<>(parsed);
      parsed.clear();
      return results;
    }

    LineInfo parseLineInfo(@NotNull String line, @NotNull String category) {
      // Any carried over style info needs to be stored so it can be used by lines that need to be re-rendered.
      // (For example, if they are truncated on arrival and need to be reconstituted with full content.)
      final SimpleTextAttributes carriedOverStyle = style;

      final Kind kind = parseKind(line, category);
      // On reloads / restarts, clear cached styles in case we're in the middle of an unterminated style block.
      if (kind == FlutterLogEntry.Kind.RELOAD || kind == FlutterLogEntry.Kind.RESTART) {
        clear();
      }

      final List<StyledText> styledText = parseLineStyle(line);
      return new LineInfo(line, styledText, kind, category, filters, carriedOverStyle);
    }

    FlutterLogEntry parseEntry(@NotNull String line, @NotNull String category, int level) {
      final LineInfo lineInfo = parseLineInfo(line, category);
      return new FlutterLogEntry(System.currentTimeMillis(), lineInfo, level);
    }
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
        return lineHandler.parseEntry(line, TOOLS_CATEGORY, UNDEFINED_LEVEL.value);
      }
    }

    return null;
  }

  public FlutterLogEntry parseConsoleEvent(String text, ConsoleViewContentType type) {
    if (type == ConsoleViewContentType.NORMAL_OUTPUT) {
      return parseDaemonEvent(text);
    }
    // TODO(pq): handle else (errors, etc).
    return null;
  }

  public void setVmServices(@NotNull FlutterApp app, @NotNull VmService vmService) {
    this.app = app;
    inspectorObjectGroup = InspectorService.createGroup(app, app.getFlutterDebugProcess(), vmService, "console-group");
  }
}

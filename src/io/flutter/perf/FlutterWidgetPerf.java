/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EdtInvocationManager;
import io.flutter.utils.AsyncUtils;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.flutter.inspector.InspectorService.toSourceLocationUri;

/**
 * This class provides the glue code between code fetching performance
 * statistics json from a running flutter application and the ui rendering the
 * performance statistics directly within the text editors.
 *
 * This class is written to be amenable to unittesting unlike
 * FlutterWidgetPerfManager so try to put all complex logic in this class
 * so that issues can be caught by unittests.
 *
 * See EditorPerfDecorations which performs all of the concrete ui rendering
 * and VmServiceWidgetPerfProvider which performs fetching of json from a
 * production application.
 */
public class FlutterWidgetPerf implements Disposable, Repaintable {

  // Retry requests if we do not receive a response within this interval.
  private static final long REQUEST_TIMEOUT_INTERVAL = 2000;

  // Intentionally use a low FPS as the animations in EditorPerfDecorations
  // are quite CPU intensive due to animating content in TextEditor windows.
  private static final int UI_FPS = 8;
  private boolean isDirty = true;
  private boolean requestInProgress = false;
  private long lastRequestTime;

  private final Map<TextEditor, EditorPerfModel> editorDecorations = new HashMap<>();
  final Set<TextEditor> currentEditors = new HashSet<>();
  private boolean profilingEnabled = false;
  final Timer uiAnimationTimer;
  private final WidgetPerfProvider perfProvider;
  private boolean isDisposed = false;
  private final FilePerfModelFactory perfModelFactory;
  private final FileLocationMapperFactory fileLocationMapperFactory;

  FlutterWidgetPerf(boolean profilingEnabled, WidgetPerfProvider perfProvider,
                    FilePerfModelFactory perfModelFactory,
                    FileLocationMapperFactory fileLocationMapperFactory) {
    this.profilingEnabled = profilingEnabled;
    this.perfProvider = perfProvider;
    this.perfModelFactory = perfModelFactory;
    this.fileLocationMapperFactory = fileLocationMapperFactory;

    perfProvider.setTarget(this);
    uiAnimationTimer = new Timer(1000 / UI_FPS, this::onFrame);
  }

  // The logic for when requests are in progress is fragile. This helper
  // method exists to we have a single place to instrument to track when
  // request status changes to help debug issues,.
  private void setRequestInProgress(boolean value) {
    requestInProgress = value;
  }

  private void onFrame(ActionEvent event) {
    for (EditorPerfModel decorations : editorDecorations.values()) {
      decorations.onFrame();
    }
  }

  private boolean isConnected() {
    return perfProvider.isConnected();
  }

  /**
   * Schedule a repaint of the widget perf information.
   * <p>
   * When.now schedules a repaint immediately.
   * <p>
   * When.soon will schedule a repaint shortly; that can get delayed by another request, with a maximum delay.
   */
  @Override
  public void requestRepaint(When when) {
    if (!profilingEnabled) {
      isDirty = false;
      return;
    }
    isDirty = true;

    if (!isConnected() || this.currentEditors.isEmpty()) {
      return;
    }

    final long currentTime = System.currentTimeMillis();
    if (requestInProgress && (currentTime - lastRequestTime) < REQUEST_TIMEOUT_INTERVAL) {
      return;
    }
    setRequestInProgress(true);
    lastRequestTime = currentTime;

    final TextEditor[] editors = this.currentEditors.toArray(new TextEditor[0]);

    JobScheduler.getScheduler().schedule(() -> performRequest(editors), 0, TimeUnit.SECONDS);
  }

  void setProfilingEnabled(boolean enabled) {
    profilingEnabled = enabled;
  }

  private void performRequest(TextEditor[] fileEditors) {
    assert !EdtInvocationManager.getInstance().isEventDispatchThread();

    if (!profilingEnabled) {
      setRequestInProgress(false);
      return;
    }

    final Multimap<String, TextEditor> editorForPath = LinkedListMultimap.create();
    final List<String> uris = new ArrayList<>();
    for (TextEditor editor : fileEditors) {
      final VirtualFile file = editor.getFile();
      if (file == null) {
        continue;
      }
      final String uri = toSourceLocationUri(file.getPath());
      editorForPath.put(uri, editor);
      uris.add(uri);
    }
    if (uris.isEmpty()) {
      setRequestInProgress(false);
      return;
    }

    isDirty = false;

    AsyncUtils.whenCompleteUiThread(perfProvider.getPerfSourceReports(uris), (JsonObject object, Throwable e) -> {
      if (e != null || object == null) {
        performRequestFinish(fileEditors);
        return;
      }
      // True if any of the EditorPerfDecorations want to animate.
      boolean animate = false;
      for (String path : editorForPath.keySet()) {
        final JsonObject result = object.getAsJsonObject("result");
        if (result == null) {
          performRequestFinish(fileEditors);
          return;
        }
        final List<PerfSourceReport> reports = new ArrayList<>();
        if (result.has(path)) {
          final JsonObject jsonForFile = result.getAsJsonObject(path);
          for (PerfReportKind kind : PerfReportKind.values()) {
            if (jsonForFile.has(kind.name)) {
              reports.add(new PerfSourceReport(jsonForFile.getAsJsonArray(kind.name), kind));
            }
          }
        }
        for (TextEditor fileEditor : editorForPath.get(path)) {
          // Ensure the fileEditor is still dealing with this file.
          // TODO(jacobr): can file editors really change their associated file?
          if (fileEditor.getFile() != null && toSourceLocationUri(fileEditor.getFile().getPath()).equals(path)) {
            final EditorPerfModel editorDecoration = editorDecorations.get(fileEditor);
            if (editorDecoration != null) {

              if (!perfProvider.shouldDisplayPerfStats(fileEditor)) {
                editorDecoration.clear();
                continue;
              }
              final FileLocationMapper fileLocationMapper = fileLocationMapperFactory.create(fileEditor);
              final FilePerfInfo stats = new FilePerfInfo();
              for (PerfSourceReport report : reports) {
                for (PerfSourceReport.Entry entry : report.getEntries()) {
                  final TextRange range = fileLocationMapper.getIdentifierRange(entry.line, entry.column);
                  if (range == null) {
                    continue;
                  }
                  stats.add(
                    range,
                    new SummaryStats(
                      report.getKind(),
                      entry.total,
                      entry.pastSecond,
                      fileLocationMapper.getText(range)
                    )
                  );
                }
              }

              editorDecoration.setPerfInfo(stats);
              if (editorDecoration.isAnimationActive()) {
                animate = true;
              }
            }
          }
        }
      }
      if (animate != uiAnimationTimer.isRunning()) {
        if (animate) {
          uiAnimationTimer.start();
        }
        else {
          uiAnimationTimer.stop();
        }
      }
      performRequestFinish(fileEditors);
    });
  }

  private void performRequestFinish(FileEditor[] editors) {
    setRequestInProgress(false);
    JobScheduler.getScheduler().schedule(() -> maybeNotifyIdle(), 1, TimeUnit.SECONDS);
    if (isDirty) {
      requestRepaint(When.soon);
    }
  }

  private void maybeNotifyIdle() {
    if (System.currentTimeMillis() >= lastRequestTime + 1000) {
      ApplicationManager.getApplication().invokeLater(() -> {
        for (EditorPerfModel decoration : editorDecorations.values()) {
          decoration.markAppIdle();
        }
        uiAnimationTimer.stop();
      });
    }
  }

  public void showFor(Set<TextEditor> editors) {
    currentEditors.clear();
    currentEditors.addAll(editors);

    // Harvest old editors.
    harvestInvalidEditors(editors);

    for (TextEditor fileEditor : currentEditors) {
      // Create a new EditorPerfModel if necessary.
      if (!editorDecorations.containsKey(fileEditor)) {
        editorDecorations.put(fileEditor, perfModelFactory.create((TextEditor)fileEditor));
      }
    }
    requestRepaint(When.now);
  }

  private void harvestInvalidEditors(Set<TextEditor> newEditors) {
    final Iterator<TextEditor> editors = editorDecorations.keySet().iterator();

    while (editors.hasNext()) {
      final TextEditor editor = editors.next();
      if (!editor.isValid() || (newEditors != null && !newEditors.contains(editor))) {
        final EditorPerfModel editorPerfDecorations = editorDecorations.get(editor);
        editors.remove();
        editorPerfDecorations.dispose();
      }
    }
  }

  @Override
  public void dispose() {
    if (isDisposed) {
      return;
    }

    this.isDisposed = true;

    perfProvider.dispose();

    clearDecorations();
    for (EditorPerfModel decorations : editorDecorations.values()) {
      decorations.dispose();
    }
    editorDecorations.clear();
  }

  void clearDecorations() {
    for (EditorPerfModel decorations : editorDecorations.values()) {
      decorations.clear();
    }
  }

  public void clear() {
    ApplicationManager.getApplication().invokeLater(this::clearDecorations);
  }
}

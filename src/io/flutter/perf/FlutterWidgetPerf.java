/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EdtInvocationManager;
import gnu.trove.TIntObjectHashMap;
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
 * <p>
 * This class is written to be amenable to unittesting unlike
 * FlutterWidgetPerfManager so try to put all complex logic in this class
 * so that issues can be caught by unittests.
 * <p>
 * See EditorPerfDecorations which performs all of the concrete ui rendering
 * and VmServiceWidgetPerfProvider which performs fetching of json from a
 * production application.
 */
public class FlutterWidgetPerf implements Disposable, WidgetPerfListener {

  // Retry requests if we do not receive a response within this interval.
  private static final long REQUEST_TIMEOUT_INTERVAL = 2000;

  // Intentionally use a low FPS as the animations in EditorPerfDecorations
  // are quite CPU intensive due to animating content in TextEditor windows.
  private static final int UI_FPS = 8;
  private boolean isDirty = true;
  private boolean requestInProgress = false;
  private long lastRequestTime;

  private final Map<TextEditor, EditorPerfModel> editorDecorations = new HashMap<>();
  private final TIntObjectHashMap<Location> knownLocationIds = new TIntObjectHashMap<>();
  private final SetMultimap<String, Location> locationsPerFile = HashMultimap.create();
  private final Map<PerfReportKind, TIntObjectHashMap<SlidingWindowStats>> stats = new HashMap<>();

  final Set<TextEditor> currentEditors = new HashSet<>();
  private boolean profilingEnabled = false;
  final Timer uiAnimationTimer;
  private final WidgetPerfProvider perfProvider;
  private boolean isDisposed = false;
  private final FilePerfModelFactory perfModelFactory;
  private final FileLocationMapperFactory fileLocationMapperFactory;
  private int lastStartTime = -1;
  private volatile long lastLocalPerfEventTime;
  private final WidgetPerfLinter perfLinter;

  FlutterWidgetPerf(boolean profilingEnabled, WidgetPerfProvider perfProvider,
                    FilePerfModelFactory perfModelFactory,
                    FileLocationMapperFactory fileLocationMapperFactory) {
    this.profilingEnabled = profilingEnabled;
    this.perfProvider = perfProvider;
    this.perfModelFactory = perfModelFactory;
    this.fileLocationMapperFactory = fileLocationMapperFactory;
    this.perfLinter = new WidgetPerfLinter(this, perfProvider);

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

  public long getLastLocalPerfEventTime() {
    return lastLocalPerfEventTime;
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
    AsyncUtils.invokeLater(() -> performRequest(editors));
  }

  @Override
  public void onWidgetPerfEvent(PerfReportKind kind, JsonObject json) {
    synchronized (this) {
      final long startTimeMicros = json.get("startTime").getAsLong();
      final int startTimeMilis = (int)(startTimeMicros / 1000);
      lastLocalPerfEventTime = System.currentTimeMillis();
      if (lastStartTime > startTimeMilis) {
        // We went backwards in time. There must have been a hot restart so
        // clear all old stats.
        for (TIntObjectHashMap<SlidingWindowStats> statsForKind : stats.values()) {
          statsForKind.forEachValue((SlidingWindowStats entry) -> {
            entry.clear();
            return true;
          });
        }
      }
      lastStartTime = startTimeMilis;

      if (json.has("newLocations")) {
        final JsonObject newLocations = json.getAsJsonObject("newLocations");
        for (Map.Entry<String, JsonElement> entry : newLocations.entrySet()) {
          final String path = entry.getKey();
          final JsonArray entries = entry.getValue().getAsJsonArray();
          assert (entries.size() % 3 == 0);
          for (int i = 0; i < entries.size(); i += 3) {
            final int id = entries.get(i).getAsInt();
            final int line = entries.get(i + 1).getAsInt();
            final int column = entries.get(i + 2).getAsInt();
            final Location location = new Location(path, line, column, id);
            final Location existingLocation = knownLocationIds.get(id);
            if (existingLocation == null) {
              addNewLocation(id, location);
            }
            else {
              if (!location.equals(existingLocation)) {
                // Cleanup all references to the old location as it is stale.
                // This occurs if there is a hot restart or reload that we weren't aware of.
                locationsPerFile.remove(existingLocation.path, existingLocation);
                for (TIntObjectHashMap<SlidingWindowStats> statsForKind : stats.values()) {
                  statsForKind.remove(id);
                }
                addNewLocation(id, location);
              }
            }
          }
        }
      }
      final TIntObjectHashMap<SlidingWindowStats> statsForKind = getStatsForKind(kind);
      final PerfSourceReport report = new PerfSourceReport(json.getAsJsonArray("events"), kind, startTimeMicros);
      for (PerfSourceReport.Entry entry : report.getEntries()) {
        final int locationId = entry.locationId;
        SlidingWindowStats statsForLocation = statsForKind.get(locationId);
        if (statsForLocation == null) {
          statsForLocation = new SlidingWindowStats();
          statsForKind.put(locationId, statsForLocation);
        }
        statsForLocation.add(entry.total, startTimeMilis);
      }
    }
  }

  @Override
  public void onNavigation() {
    synchronized (this) {
      for (TIntObjectHashMap<SlidingWindowStats> statsForKind : stats.values()) {
        statsForKind.forEachValue((SlidingWindowStats entry) -> {
          entry.onNavigation();
          return true;
        });
      }
    }
  }

  private TIntObjectHashMap<SlidingWindowStats> getStatsForKind(PerfReportKind kind) {
    TIntObjectHashMap<SlidingWindowStats> report = stats.get(kind);
    if (report == null) {
      report = new TIntObjectHashMap<>();
      stats.put(kind, report);
    }
    return report;
  }

  private void addNewLocation(int id, Location location) {
    knownLocationIds.put(id, location);
    locationsPerFile.put(location.path, location);
  }

  void setProfilingEnabled(boolean enabled) {
    profilingEnabled = enabled;
  }

  private void performRequest(TextEditor[] fileEditors) {
    assert EdtInvocationManager.getInstance().isEventDispatchThread();

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

    showReports(editorForPath);
  }

  private void showReports(Multimap<String, TextEditor> editorForPath) {
    // True if any of the EditorPerfDecorations want to animate.
    boolean animate = false;

    synchronized (this) {
      for (String path : editorForPath.keySet()) {
        for (TextEditor fileEditor : editorForPath.get(path)) {
          // Ensure the fileEditor is still dealing with this path.
          // TODO(jacobr): can file editors really change their associated path?
          if (fileEditor.getFile() != null && toSourceLocationUri(fileEditor.getFile().getPath()).equals(path)) {
            final EditorPerfModel editorDecoration = editorDecorations.get(fileEditor);
            if (editorDecoration != null) {
              if (!perfProvider.shouldDisplayPerfStats(fileEditor)) {
                editorDecoration.clear();
                continue;
              }
              final FilePerfInfo fileStats = buildSummaryStats(fileEditor);
              editorDecoration.setPerfInfo(fileStats);
              if (editorDecoration.isAnimationActive()) {
                animate = true;
              }
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
    performRequestFinish();
  }

  FilePerfInfo buildSummaryStats(TextEditor fileEditor) {
    final String path = toSourceLocationUri(fileEditor.getFile().getPath());
    final FileLocationMapper fileLocationMapper = fileLocationMapperFactory.create(fileEditor);
    final FilePerfInfo fileStats = new FilePerfInfo();
    for (PerfReportKind kind : PerfReportKind.values()) {
      final TIntObjectHashMap<SlidingWindowStats> statsForKind = stats.get(kind);
      if (statsForKind == null) {
        continue;
      }
      for (Location location : locationsPerFile.get(path)) {
        final SlidingWindowStats entry = statsForKind.get(location.id);
        if (entry == null) {
          continue;
        }
        final TextRange range = fileLocationMapper.getIdentifierRange(location.line, location.column);
        if (range == null) {
          continue;
        }
        fileStats.add(
          range,
          new SummaryStats(
            kind,
            new SlidingWindowStatsSummary(entry, lastStartTime, location),
            fileLocationMapper.getText(range)
          )
        );
      }
    }
    return fileStats;
  }

  private void performRequestFinish() {
    setRequestInProgress(false);
    JobScheduler.getScheduler().schedule(this::maybeNotifyIdle, 1, TimeUnit.SECONDS);
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
        editorDecorations.put(fileEditor, perfModelFactory.create(fileEditor));
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

  private void onRestartHelper() {
    // The app has restarted. Location ids may not be valid.
    knownLocationIds.clear();
    stats.clear();
    clearDecorations();
  }

  public void onRestart() {
    ApplicationManager.getApplication().invokeLater(this::onRestartHelper);
  }

  public WidgetPerfLinter getPerfLinter() {
    return perfLinter;
  }
}

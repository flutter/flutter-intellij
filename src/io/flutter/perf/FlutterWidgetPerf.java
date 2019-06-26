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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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

  public static final long IDLE_DELAY_MILISECONDS = 400;

  class StatsForReportKind {
    final Map<Integer, SlidingWindowStats> data = new HashMap<>();
    private int lastStartTime = -1;
    private int lastNonEmptyReportTime = -1;
  }

  // Retry requests if we do not receive a response within this interval.
  private static final long REQUEST_TIMEOUT_INTERVAL = 2000;

  // Intentionally use a low FPS as the animations in EditorPerfDecorations
  // are quite CPU intensive due to animating content in TextEditor windows.
  private static final int UI_FPS = 8;
  private boolean isDirty = true;
  private boolean requestInProgress = false;
  private long lastRequestTime;

  private final Set<PerfModel> perfListeners = new HashSet<>();

  private final Map<TextEditor, EditorPerfModel> editorDecorations = new HashMap<>();
  private final Map<Integer, Location> knownLocationIds = new HashMap<>();
  private final SetMultimap<String, Location> locationsPerFile = HashMultimap.create();
  private final Map<PerfReportKind, StatsForReportKind> stats = new HashMap<>();

  final Set<TextEditor> currentEditors = new HashSet<>();
  private boolean profilingEnabled;
  final Timer uiAnimationTimer;
  private final WidgetPerfProvider perfProvider;
  private boolean isDisposed = false;
  private final FilePerfModelFactory perfModelFactory;
  private final FileLocationMapperFactory fileLocationMapperFactory;
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

    for (PerfModel model : perfListeners) {
      model.onFrame();
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

    if (!isConnected() || (this.currentEditors.isEmpty() && this.perfListeners.isEmpty())) {
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
    // Read access to the Document objects on background thread is needed so
    // a ReadAction is required. Document objects are used to determine the
    // widget names at specific locations in documents.
    final Runnable action = () -> {
      synchronized (this) {
        final long startTimeMicros = json.get("startTime").getAsLong();
        final int startTimeMilis = (int)(startTimeMicros / 1000);
        lastLocalPerfEventTime = System.currentTimeMillis();
        final StatsForReportKind statsForReportKind = getStatsForKind(kind);
        if (statsForReportKind.lastStartTime > startTimeMilis) {
          // We went backwards in time. There must have been a hot restart so
          // clear all old stats.
          statsForReportKind.data.forEach((Integer id, SlidingWindowStats entry) -> {
            entry.clear();
          });
        }
        statsForReportKind.lastStartTime = startTimeMilis;

        if (json.has("newLocations")) {
          final JsonObject newLocations = json.getAsJsonObject("newLocations");
          for (Map.Entry<String, JsonElement> entry : newLocations.entrySet()) {
            final String path = entry.getKey();
            FileLocationMapper locationMapper = fileLocationMapperFactory.create(path);
            final JsonArray entries = entry.getValue().getAsJsonArray();
            assert (entries.size() % 3 == 0);
            for (int i = 0; i < entries.size(); i += 3) {
              final int id = entries.get(i).getAsInt();
              final int line = entries.get(i + 1).getAsInt();
              final int column = entries.get(i + 2).getAsInt();
              final TextRange textRange = locationMapper.getIdentifierRange(line, column);
              final String name = locationMapper.getText(textRange);
              assert (name != null);
              final Location location = new Location(path, line, column, id, textRange, name);

              final Location existingLocation = knownLocationIds.get(id);
              if (existingLocation == null) {
                addNewLocation(id, location);
              }
              else {
                if (!location.equals(existingLocation)) {
                  // Cleanup all references to the old location as it is stale.
                  // This occurs if there is a hot restart or reload that we weren't aware of.
                  locationsPerFile.remove(existingLocation.path, existingLocation);
                  for (StatsForReportKind statsForKind : stats.values()) {
                    statsForKind.data.remove(id);
                  }
                  addNewLocation(id, location);
                }
              }
            }
          }
        }
        final StatsForReportKind statsForKind = getStatsForKind(kind);
        final PerfSourceReport report = new PerfSourceReport(json.getAsJsonArray("events"), kind, startTimeMicros);
        if (report.getEntries().size() > 0) {
          statsForReportKind.lastNonEmptyReportTime = startTimeMilis;
        }
        for (PerfSourceReport.Entry entry : report.getEntries()) {
          final int locationId = entry.locationId;
          SlidingWindowStats statsForLocation = statsForKind.data.get(locationId);
          if (statsForLocation == null) {
            statsForLocation = new SlidingWindowStats();
            statsForKind.data.put(locationId, statsForLocation);
          }
          statsForLocation.add(entry.total, startTimeMilis);
        }
      }
    };

    final Application application = ApplicationManager.getApplication();
    if (application != null) {
      application.runReadAction(action);
    } else {
      // Unittest case.
      action.run();
    }
  }

  @Override
  public void onNavigation() {
    synchronized (this) {
      for (StatsForReportKind statsForKind : stats.values()) {
        statsForKind.data.forEach((Integer id, SlidingWindowStats entry) -> {
          entry.onNavigation();
        });
      }
    }
  }

  @Override
  public void addPerfListener(PerfModel listener) {
    perfListeners.add(listener);
  }

  @Override
  public void removePerfListener(PerfModel listener) {
    perfListeners.remove(listener);
  }

  private StatsForReportKind getStatsForKind(PerfReportKind kind) {
    StatsForReportKind report = stats.get(kind);
    if (report == null) {
      report = new StatsForReportKind();
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
    if (uris.isEmpty() && perfListeners.isEmpty()) {
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

    if (!animate) {
      for (PerfModel listener : perfListeners) {
        if (listener.isAnimationActive()) {
          animate = true;
          break;
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

  private FilePerfInfo buildSummaryStats(TextEditor fileEditor) {
    final String path = toSourceLocationUri(fileEditor.getFile().getPath());
    final FilePerfInfo fileStats = new FilePerfInfo();
    for (PerfReportKind kind : PerfReportKind.values()) {
      final StatsForReportKind forKind = stats.get(kind);
      if (forKind == null) {
        continue;
      }
      final Map<Integer, SlidingWindowStats> data = forKind.data;
      for (Location location : locationsPerFile.get(path)) {
        final SlidingWindowStats entry = data.get(location.id);
        if (entry == null) {
          continue;
        }
        final TextRange range = location.textRange;
        if (range == null) {
          continue;
        }
        fileStats.add(
          range,
          new SummaryStats(
            kind,
            new SlidingWindowStatsSummary(entry, forKind.lastStartTime, location),
            location.name
          )
        );
      }
    }
    return fileStats;
  }

  private void performRequestFinish() {
    setRequestInProgress(false);
    JobScheduler.getScheduler().schedule(this::maybeNotifyIdle, IDLE_DELAY_MILISECONDS, TimeUnit.MILLISECONDS);
    if (isDirty) {
      requestRepaint(When.soon);
    }
  }

  private void maybeNotifyIdle() {
    if (isDisposed) {
      return;
    }
    if (System.currentTimeMillis() >= lastRequestTime + IDLE_DELAY_MILISECONDS) {
      AsyncUtils.invokeLater(() -> {
        for (EditorPerfModel decoration : editorDecorations.values()) {
          decoration.markAppIdle();
        }
        for (PerfModel listener : perfListeners) {
          listener.markAppIdle();
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

  public void setAlwaysShowLineMarkersOverride(boolean show) {
    for (EditorPerfModel model : editorDecorations.values()) {
      model.setAlwaysShowLineMarkersOverride(show);
    }
  }

  @Override
  public void dispose() {
    if (isDisposed) {
      return;
    }

    this.isDisposed = true;

    if (uiAnimationTimer.isRunning()) {
      uiAnimationTimer.stop();
    }
    perfProvider.dispose();

    clearModels();
    for (EditorPerfModel decorations : editorDecorations.values()) {
      decorations.dispose();
    }
    editorDecorations.clear();
    perfListeners.clear();
  }

  void clearModels() {
    for (EditorPerfModel decorations : editorDecorations.values()) {
      decorations.clear();
    }
    for (PerfModel listener: perfListeners) {
      listener.clear();
    }
  }

  public void clear() {
    ApplicationManager.getApplication().invokeLater(this::clearModels);
  }

  private void onRestartHelper() {
    // The app has restarted. Location ids may not be valid.
    knownLocationIds.clear();
    stats.clear();
    clearModels();
  }

  public void onRestart() {
    ApplicationManager.getApplication().invokeLater(this::onRestartHelper);
  }

  public WidgetPerfLinter getPerfLinter() {
    return perfLinter;
  }

  public ArrayList<FilePerfInfo> buildAllSummaryStats(Set<TextEditor> textEditors) {
    final ArrayList<FilePerfInfo> stats = new ArrayList<>();
    synchronized (this) {
      for (TextEditor textEditor : textEditors) {
        stats.add(buildSummaryStats(textEditor));
      }
    }
    return stats;
  }

  public ArrayList<SlidingWindowStatsSummary> getStatsForMetric(ArrayList<PerfMetric> metrics, PerfReportKind kind) {
    final ArrayList<SlidingWindowStatsSummary> entries = new ArrayList<>();
    synchronized (this) {
      final StatsForReportKind forKind = stats.get(kind);
      if (forKind != null) {
        final int time = forKind.lastNonEmptyReportTime;
        forKind.data.forEach((Integer locationId, SlidingWindowStats stats) -> {
          for (PerfMetric metric : metrics) {
            if (stats.getValue(metric, time) > 0) {
              final Location location = knownLocationIds.get(locationId);
              // TODO(jacobr): consider changing this check for
              // location != null to an assert once the edge case leading to
              // occassional null locations has been fixed. I expect the edge
              // case occurs because we are sometimes including a few stats
              // from before a hot restart due to an incorrect ordering for
              // when the events occur. In any case, the extra != null check
              // is harmless and ensures the UI display is robust at the cost
              // of perhaps ommiting a little likely stale data.
              // See https://github.com/flutter/flutter-intellij/issues/2892
              if (location != null) {
                entries.add(new SlidingWindowStatsSummary(
                  stats,
                  time,
                  location
                ));
              }
            }
          }
        });
      }
    }
    return entries;
  }
}

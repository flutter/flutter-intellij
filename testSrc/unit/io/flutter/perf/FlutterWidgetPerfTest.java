/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.flutter.perf.FlutterWidgetPerf.IDLE_DELAY_MILISECONDS;
import static org.junit.Assert.*;

class MockWidgetPerfProvider implements WidgetPerfProvider {

  WidgetPerfListener widgetPerfListener;
  boolean isDisposed = false;
  boolean shouldDisplayStats = true;
  List<List<String>> requests = new ArrayList<>();
  List<Iterable<Integer>> locationIdRequests = new ArrayList<>();

  @Override
  public void setTarget(WidgetPerfListener widgetPerfListener) {
    this.widgetPerfListener = widgetPerfListener;
  }

  @Override
  public boolean isStarted() {
    return true;
  }

  @Override
  public boolean isConnected() {
    return true;
  }

  @Override
  public boolean shouldDisplayPerfStats(FileEditor editor) {
    return shouldDisplayStats;
  }

  @Override
  public CompletableFuture<DiagnosticsNode> getWidgetTree() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }

  public void simulateWidgetPerfEvent(PerfReportKind kind, String json) {
    widgetPerfListener.onWidgetPerfEvent(kind, (JsonObject)JsonUtils.parseString(json));
  }

  public void repaint(When when) {
    widgetPerfListener.requestRepaint(when);
  }
}

class MockPerfModel implements PerfModel {

  volatile int idleCount = 0;
  volatile int clearCount = 0;
  volatile int frameCount = 0;

  @Override
  public void markAppIdle() {
    idleCount++;
  }

  @Override
  public void clear() {
    clearCount++;
  }

  @Override
  public void onFrame() {
    frameCount++;
  }

  @Override
  public boolean isAnimationActive() {
    return idleCount == 0;
  }
}

class MockEditorPerfModel extends MockPerfModel implements EditorPerfModel {

  public boolean isHoveredOverLineMarkerAreaOverride = false;
  boolean isDisposed;
  CompletableFuture<FilePerfInfo> statsFuture;
  private FlutterApp app;
  private FilePerfInfo stats;
  private final TextEditor textEditor;

  MockEditorPerfModel(TextEditor textEditor) {
    this.textEditor = textEditor;
    statsFuture = new CompletableFuture<>();
  }

  @NotNull
  @Override
  public FilePerfInfo getStats() {
    return stats;
  }

  CompletableFuture<FilePerfInfo> getStatsFuture() {
    return statsFuture;
  }

  @NotNull
  @Override
  public TextEditor getTextEditor() {
    return textEditor;
  }

  @Override
  public FlutterApp getApp() {
    return app;
  }

  @Override
  public boolean getAlwaysShowLineMarkers() {
    return isHoveredOverLineMarkerAreaOverride;
  }

  @Override
  public void setAlwaysShowLineMarkersOverride(boolean show) {
  }

  @Override
  public void markAppIdle() {
    super.markAppIdle();
    stats.markAppIdle();
  }

  @Override
  public void clear() {
    super.clear();
    stats.clear();
  }

  @Override
  public void setPerfInfo(FilePerfInfo stats) {
    this.stats = stats;
    if (statsFuture.isDone()) {
      statsFuture = new CompletableFuture<>();
    }
    statsFuture.complete(stats);
  }

  @Override
  public boolean isAnimationActive() {
    return stats.getTotalValue(PerfMetric.peakRecent) > 0;
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }
}

class FakeFileLocationMapper implements FileLocationMapper {

  private final String path;

  FakeFileLocationMapper(String path) {
    this.path = path;
  }

  Map<TextRange, String> mockText = new HashMap<>();

  @Override
  public TextRange getIdentifierRange(int line, int column) {
    // Bogus but unique.
    final int offset = line * 1000 + column;
    // Dummy name so we can tell what the original line and column was in tests
    final String text = "Widget:" + line + ":" + column;
    final TextRange range = new TextRange(offset, offset + text.length());
    mockText.put(range, text);
    return range;
  }

  @Override
  public String getText(TextRange textRange) {
    assert mockText.containsKey(textRange);
    return mockText.get(textRange);
  }

  @Override
  public String getPath() {
    return path;
  }
}

class MockTextEditor implements TextEditor {
  static MockVirtualFileSystem fileSystem = new MockVirtualFileSystem();
  private final String name;
  private final VirtualFile file;
  private boolean modified;

  MockTextEditor(String name) {
    this.name = name;
    file = fileSystem.findFileByPath(name);
  }

  @NotNull
  @Override
  public Editor getEditor() {
    return null;
  }

  @Override
  public VirtualFile getFile() {
    return file;
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return false;
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {

  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return null;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

  }

  @Override
  public boolean isModified() {
    return modified;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {

  }

  @Override
  public void deselectNotify() {

  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {

  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {

  }
}

public class FlutterWidgetPerfTest {

  private TextRange getTextRange(String path, int line, int column) {
    return new FakeFileLocationMapper(path).getIdentifierRange(line, column);
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/4343")
  public void testFileStatsCalculation() throws InterruptedException, ExecutionException {
    final MockWidgetPerfProvider widgetPerfProvider = new MockWidgetPerfProvider();

    final Map<String, MockEditorPerfModel> perfModels = new HashMap<>();
    final FlutterWidgetPerf flutterWidgetPerf = new FlutterWidgetPerf(
      true,
      widgetPerfProvider,
      (TextEditor textEditor) -> {
        final MockEditorPerfModel model = new MockEditorPerfModel(textEditor);
        perfModels.put(textEditor.getName(), model);
        return model;
      },
      path -> new FakeFileLocationMapper(path)
    );
    widgetPerfProvider.simulateWidgetPerfEvent(PerfReportKind.rebuild,
                                               "{\"startTime\":1000,\"events\":[1,1,2,1,3,1,4,1,6,1,10,4,11,4,12,4,13,1,14,1,95,1,96,1,97,6,100,6,102,6,104,6,105,1,106,1],\"newLocations\":{\"/sample/project/main.dart\":[1,11,14,2,18,16,3,23,17,4,40,16,6,46,16,10,69,9,11,70,9,12,71,18,13,41,19,14,42,20,95,51,58],\"/sample/project/clock.dart\":[96,33,12,97,52,12,100,53,16,102,54,14,104,55,17,105,34,15,106,35,16]}}");

    // Simulate 60fps for 2 seconds.
    for (int frame = 1; frame <= 120; frame++) {
      final long startTime = 1000 + frame * 1000 * 1000 / 60;
      widgetPerfProvider.simulateWidgetPerfEvent(PerfReportKind.rebuild, "{\"startTime\":" +
                                                                         startTime +
                                                                         ",\"events\":[95,1,96,1,97,6,100,6,102,6,104,6,105,1,106,1]}");
    }
    final Set<TextEditor> textEditors = new HashSet<>();
    final TextEditor clockTextEditor = new MockTextEditor("/sample/project/clock.dart");
    textEditors.add(clockTextEditor);
    flutterWidgetPerf.showFor(textEditors);
    assertEquals(perfModels.size(), 1);
    MockEditorPerfModel clockModel = perfModels.get("/sample/project/clock.dart");
    assertEquals(clockModel.getTextEditor(), clockTextEditor);

    FilePerfInfo stats = clockModel.getStatsFuture().get();
    // Stats for the first response are rebuilds only
    assertEquals(1620, stats.getTotalValue(PerfMetric.pastSecond));
    List<TextRange> locations = Lists.newArrayList(stats.getLocations());
    assertEquals(7, locations.size());
    TextRange textRange = getTextRange("/sample/project/clock.dart", 52, 12);
    List<SummaryStats> rangeStats = Lists.newArrayList(stats.getRangeStats(textRange));

    assertEquals(1, rangeStats.size());

    assertEquals(PerfReportKind.rebuild, rangeStats.get(0).getKind());
    assertEquals(360, rangeStats.get(0).getValue(PerfMetric.pastSecond));
    assertEquals(726, rangeStats.get(0).getValue(PerfMetric.total));
    assertEquals("Widget:52:12", rangeStats.get(0).getDescription());

    rangeStats = Lists.newArrayList(stats.getRangeStats(getTextRange("/sample/project/clock.dart", 34, 15)));

    assertEquals(1, rangeStats.size());
    assertEquals(PerfReportKind.rebuild, rangeStats.get(0).getKind());
    assertEquals(60, rangeStats.get(0).getValue(PerfMetric.pastSecond));
    assertEquals(121, rangeStats.get(0).getValue(PerfMetric.total));
    assertEquals("Widget:34:15", rangeStats.get(0).getDescription());

    clockModel.markAppIdle();
    assertEquals(0, stats.getTotalValue(PerfMetric.pastSecond));
    rangeStats = Lists.newArrayList(stats.getRangeStats(locations.get(4)));
    assertEquals(0, rangeStats.get(0).getValue(PerfMetric.pastSecond));
    // Total is not impacted.
    assertEquals(121, rangeStats.get(0).getValue(PerfMetric.total));

    rangeStats = Lists.newArrayList(stats.getRangeStats(textRange));

    assertEquals(1, rangeStats.size());
    assertEquals(PerfReportKind.rebuild, rangeStats.get(0).getKind());
    assertEquals(0, rangeStats.get(0).getValue(PerfMetric.pastSecond));
    assertEquals(726, rangeStats.get(0).getValue(PerfMetric.total));
    assertEquals("Widget:52:12", rangeStats.get(0).getDescription());

    final TextEditor mainTextEditor = new MockTextEditor("/sample/project/main.dart");
    textEditors.add(mainTextEditor);

    flutterWidgetPerf.clearModels();
    // Add events with both rebuilds and repaints.
    widgetPerfProvider.simulateWidgetPerfEvent(PerfReportKind.rebuild,
                                               "{\"startTime\":19687239,\"events\":[95,1,96,1,97,6,100,6,102,6,104,6,105,1,106,1]}");
    widgetPerfProvider.simulateWidgetPerfEvent(PerfReportKind.repaint,
                                               "{\"startTime\":19687239,\"events\":[95,1,96,1,97,6,100,6,102,6,104,6,105,1,106,1]}");

    flutterWidgetPerf.showFor(textEditors);

    assertEquals(2, perfModels.size());
    clockModel = perfModels.get("/sample/project/clock.dart");
    final MockEditorPerfModel mainModel = perfModels.get("/sample/project/main.dart");
    assert mainModel != null;

    final FilePerfInfo mainStats = mainModel.getStatsFuture().get();
    assert clockModel != null;
    stats = clockModel.getStatsFuture().get();

    // We have new fake data for the files so the count in the past second is
    // back up from zero
    assertEquals(2, mainStats.getTotalValue(PerfMetric.pastSecond));
    assertEquals(142, mainStats.getTotalValue(PerfMetric.total));

    assertEquals(3321, stats.getTotalValue(PerfMetric.total));

    locations = Lists.newArrayList(stats.getLocations());
    assertEquals(7, locations.size());
    rangeStats = Lists.newArrayList(stats.getRangeStats(getTextRange("/sample/project/clock.dart", 52, 12)));

    assertEquals(2, rangeStats.size());
    assertEquals(PerfReportKind.repaint, rangeStats.get(0).getKind());
    assertEquals(6, rangeStats.get(0).getValue(PerfMetric.pastSecond));
    assertEquals(6, rangeStats.get(0).getValue(PerfMetric.total));
    assertEquals("Widget:52:12", rangeStats.get(0).getDescription());

    assertEquals(PerfReportKind.rebuild, rangeStats.get(1).getKind());
    assertEquals(6, rangeStats.get(1).getValue(PerfMetric.pastSecond));
    assertEquals(732, rangeStats.get(1).getValue(PerfMetric.total));
    assertEquals("Widget:52:12", rangeStats.get(1).getDescription());

    rangeStats = Lists.newArrayList(stats.getRangeStats(getTextRange("/sample/project/clock.dart", 33, 12)));

    assertEquals(2, rangeStats.size());
    assertEquals(PerfReportKind.repaint, rangeStats.get(0).getKind());
    assertEquals(1, rangeStats.get(0).getValue(PerfMetric.pastSecond));
    assertEquals(1, rangeStats.get(0).getValue(PerfMetric.total));
    assertEquals("Widget:33:12", rangeStats.get(0).getDescription());

    assertEquals(PerfReportKind.rebuild, rangeStats.get(1).getKind());
    assertEquals(1, rangeStats.get(1).getValue(PerfMetric.pastSecond));
    assertEquals(122, rangeStats.get(1).getValue(PerfMetric.total));
    assertEquals("Widget:33:12", rangeStats.get(1).getDescription());

    assertFalse(clockModel.isDisposed);
    assertFalse(mainModel.isDisposed);

    flutterWidgetPerf.showFor(new HashSet<>());

    assertTrue(clockModel.isDisposed);
    assertTrue(mainModel.isDisposed);

    flutterWidgetPerf.dispose();
  }

  @Test
  public void testOverallStatsCalculation() throws InterruptedException, ExecutionException {
    final MockWidgetPerfProvider widgetPerfProvider = new MockWidgetPerfProvider();

    final FlutterWidgetPerf flutterWidgetPerf = new FlutterWidgetPerf(
      true,
      widgetPerfProvider,
      textEditor -> null,
      path -> new FakeFileLocationMapper(path)
    );
    final MockPerfModel perfModel = new MockPerfModel();
    flutterWidgetPerf.addPerfListener(perfModel);

    widgetPerfProvider.simulateWidgetPerfEvent(PerfReportKind.rebuild,
                                               "{\"startTime\":1000,\"events\":[1,1,2,1,3,1,4,1,6,1,10,4,11,4,12,4,13,1,14,1,95,1,96,1,97,6,100,6,102,6,104,6,105,1,106,1],\"newLocations\":{\"/sample/project/main.dart\":[1,11,14,2,18,16,3,23,17,4,40,16,6,46,16,10,69,9,11,70,9,12,71,18,13,41,19,14,42,20,95,51,58],\"/sample/project/clock.dart\":[96,33,12,97,52,12,100,53,16,102,54,14,104,55,17,105,34,15,106,35,16]}}");

    // Simulate 60fps for 2 seconds.
    for (int frame = 1; frame <= 120; frame++) {
      final long startTime = 1000 + frame * 1000 * 1000 / 60;
      widgetPerfProvider.simulateWidgetPerfEvent(PerfReportKind.rebuild, "{\"startTime\":" +
                                                                         startTime +
                                                                         ",\"events\":[95,1,96,1,97,6,100,6,102,6,104,6,105,1,106,1]}");
    }
    final ArrayList<PerfMetric> lastFrameOnly = new ArrayList<>();
    lastFrameOnly.add(PerfMetric.lastFrame);
    final ArrayList<PerfMetric> metrics = new ArrayList<>();
    metrics.add(PerfMetric.lastFrame);
    metrics.add(PerfMetric.totalSinceEnteringCurrentScreen);

    ArrayList<SlidingWindowStatsSummary> stats = flutterWidgetPerf.getStatsForMetric(lastFrameOnly, PerfReportKind.repaint);
    // No repaint stats are provided.
    assertTrue(stats.isEmpty());

    stats = flutterWidgetPerf.getStatsForMetric(lastFrameOnly, PerfReportKind.rebuild);
    assertEquals(8, stats.size());

    for (SlidingWindowStatsSummary stat : stats) {
      assertTrue(stat.getValue(PerfMetric.lastFrame) > 0);
    }

    stats = flutterWidgetPerf.getStatsForMetric(metrics, PerfReportKind.rebuild);
    assertEquals(18, stats.size());
    for (SlidingWindowStatsSummary stat : stats) {
      assertTrue(stat.getValue(PerfMetric.lastFrame) > 0 ||
                 stat.getValue(PerfMetric.totalSinceEnteringCurrentScreen) > 0);
    }

    /// Test that the perfModel gets notified correctly when there are
    // events to draw a frame of the ui.
    assertEquals(perfModel.frameCount, 0);
    SwingUtilities.invokeLater(() -> {
      flutterWidgetPerf.requestRepaint(When.now);
    });

    while (perfModel.frameCount == 0) {
      try {
        Thread.sleep(1);
      }
      catch (InterruptedException e) {
        fail(e.toString());
      }
    }
    assertEquals(1, perfModel.frameCount);
    assertEquals(0, perfModel.idleCount);
    // Verify that an idle event occurs once we wait the idle time delay.
    Thread.sleep(IDLE_DELAY_MILISECONDS);
    assertEquals(1, perfModel.idleCount);

    flutterWidgetPerf.removePerfListener(perfModel);
    flutterWidgetPerf.dispose();
  }
}

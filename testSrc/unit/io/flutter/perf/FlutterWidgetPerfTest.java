/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

class MockWidgetPerfProvider implements WidgetPerfProvider {

  Repaintable repaintable;
  boolean isDisposed = false;
  boolean shouldDisplayStats = true;
  List<List<String>> requests = new ArrayList<>();
  Queue<String> responses = new ArrayDeque<>();

  @Override
  public void setTarget(Repaintable repaintable) {
    this.repaintable = repaintable;
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
  public CompletableFuture<JsonObject> getPerfSourceReports(List<String> uris) {
    requests.add(uris);
    final String response = responses.remove();
    final JsonParser parser = new JsonParser();
    return CompletableFuture.completedFuture((JsonObject)parser.parse(response));
  }

  @Override
  public boolean shouldDisplayPerfStats(FileEditor editor) {
    return shouldDisplayStats;
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }

  public void addResponse(String json) {
    responses.add(json);
  }

  public void repaint(When when) {
    repaintable.requestRepaint(when);
  }
}

class MockEditorPerfModel implements EditorPerfModel {

  public boolean isHoveredOverLineMarkerAreaOverride = false;
  boolean isDisposed;
  int frameCount = 0;
  CompletableFuture<FilePerfInfo> statsFuture;
  private FlutterApp app;
  private FilePerfInfo stats;
  private TextEditor textEditor;

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
  public boolean isHoveredOverLineMarkerArea() {
    return isHoveredOverLineMarkerAreaOverride;
  }

  @Override
  public void markAppIdle() {
    stats.markAppIdle();
  }

  @Override
  public void clear() {
    stats.clear();
  }

  @Override
  public void onFrame() {
    frameCount++;
  }

  @Override
  public void setPerfInfo(FilePerfInfo stats) {
    this.stats = stats;
    statsFuture.complete(stats);
  }

  @Override
  public boolean isAnimationActive() {
    return stats.getCountPastSecond() > 0;
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }
}

class FakeFileLocationMapper implements FileLocationMapper {

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
    return false;
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
  @Test
  public void testStatCalculation() throws InterruptedException, ExecutionException {
    final MockWidgetPerfProvider widgetPerfProvider = new MockWidgetPerfProvider();
    widgetPerfProvider.addResponse(
      "{\"result\":{\"file:///sample/project/clock.dart\":{\"rebuild\":[[37,12,328,48],[74,12,1968,288],[80,16,1968,288],[84,14,1968,288],[87,17,1968,288],[49,7,328,48],[50,16,328,48]]}},\"type\":\"_extensionType\",\"method\":\"ext.flutter.inspector.getPerfSourceReports\"}\n");

    final Map<String, MockEditorPerfModel> perfModels = new HashMap<>();
    final FlutterWidgetPerf flutterWidgetPerf = new FlutterWidgetPerf(
      true,
      widgetPerfProvider,
      (TextEditor textEditor) -> {
        final MockEditorPerfModel model = new MockEditorPerfModel(textEditor);
        perfModels.put(textEditor.getName(), model);
        return model;
      },
      (TextEditor textEditor) -> new FakeFileLocationMapper()
    );
    final Set<TextEditor> textEditors = new HashSet<>();
    final TextEditor clockTextEditor = new MockTextEditor("/sample/project/clock.dart");
    textEditors.add(clockTextEditor);
    flutterWidgetPerf.showFor(textEditors);
    assertEquals(perfModels.size(), 1);
    MockEditorPerfModel clockModel = perfModels.get("/sample/project/clock.dart");
    assertEquals(clockModel.getTextEditor(), clockTextEditor);

    FilePerfInfo stats = clockModel.getStatsFuture().get();
    // Stats for the first response are rebuilds only
    assertEquals(1296, stats.getCountPastSecond());
    List<TextRange> locations = Lists.newArrayList(stats.getLocations());
    assertEquals(7, locations.size());
    List<SummaryStats> rangeStats = Lists.newArrayList(stats.getRangeStats(locations.get(0)));

    assertEquals(1, rangeStats.size());
    assertEquals(PerfReportKind.rebuild, rangeStats.get(0).getKind());
    assertEquals(48, rangeStats.get(0).getPastSecond());
    assertEquals(328, rangeStats.get(0).getTotal());
    assertEquals("Widget:37:12", rangeStats.get(0).getDescription());


    rangeStats = Lists.newArrayList(stats.getRangeStats(locations.get(4)));

    assertEquals(1, rangeStats.size());
    assertEquals(PerfReportKind.rebuild, rangeStats.get(0).getKind());
    assertEquals(288, rangeStats.get(0).getPastSecond());
    assertEquals(1968, rangeStats.get(0).getTotal());
    assertEquals("Widget:87:17", rangeStats.get(0).getDescription());

    clockModel.markAppIdle();
    assertEquals(0, stats.getCountPastSecond());
    rangeStats = Lists.newArrayList(stats.getRangeStats(locations.get(4)));
    assertEquals(0, rangeStats.get(0).getPastSecond());
    // Total is not impacted.
    assertEquals(1968, rangeStats.get(0).getTotal());

    rangeStats = Lists.newArrayList(stats.getRangeStats(locations.get(0)));

    assertEquals(1, rangeStats.size());
    assertEquals(PerfReportKind.rebuild, rangeStats.get(0).getKind());
    assertEquals(0, rangeStats.get(0).getPastSecond());
    assertEquals(328, rangeStats.get(0).getTotal());
    assertEquals("Widget:37:12", rangeStats.get(0).getDescription());

    final TextEditor mainTextEditor = new MockTextEditor("/sample/project/main.dart");
    textEditors.add(mainTextEditor);

    perfModels.clear();
    // This response has both rebuilds and repaints.
    widgetPerfProvider.addResponse(
      "{\"result\":{\"file:///sample/project/clock.dart\":{\"rebuild\":[[37,12,328,48],[74,12,1968,288],[80,16,1968,288],[84,14,1968,288],[87,17,1968,288],[49,7,328,48],[50,16,328,48]],\"repaint\":[[37,12,327,47],[52,13,327,47],[74,12,1962,282],[80,16,1962,282],[84,14,1962,282],[87,17,1962,282],[49,7,327,47],[50,16,327,47]]},\"file:///sample/project/main.dart\":{\"rebuild\":[[24,17,1,0],[41,16,1,0],[47,16,1,0],[70,9,4,0],[71,9,4,0],[72,18,4,0],[42,19,1,0],[43,20,1,0],[52,58,328,48]],\"repaint\":[[41,16,9,0],[24,17,9,0],[46,17,9,0],[47,16,9,0],[48,22,9,0],[64,12,36,0],[67,9,36,0],[70,9,36,0],[71,9,36,0],[72,18,36,0],[42,19,9,0],[43,20,9,0],[19,16,4,0],[11,14,4,0],[52,58,327,47]]}},\"type\":\"_extensionType\",\"method\":\"ext.flutter.inspector.getPerfSourceReports\"}\n");

    flutterWidgetPerf.showFor(textEditors);

    assertEquals(2, perfModels.size());
    clockModel = perfModels.get("/sample/project/clock.dart");
    final MockEditorPerfModel mainModel = perfModels.get("/sample/project/main.dart");
    assert clockModel != null;
    assert mainModel != null;

    FilePerfInfo mainStats = null;
    stats = clockModel.getStatsFuture().get();
    mainStats = mainModel.getStatsFuture().get();

    // We have new fake data for the files so the count in the past second is
    // back up from zero
    assertEquals(2612, stats.getCountPastSecond());
    assertEquals(95, mainStats.getCountPastSecond());

    locations = Lists.newArrayList(stats.getLocations());
    assertEquals(8, locations.size());
    rangeStats = Lists.newArrayList(stats.getRangeStats(locations.get(0)));

    assertEquals(2, rangeStats.size());
    assertEquals(PerfReportKind.repaint, rangeStats.get(0).getKind());
    assertEquals(47, rangeStats.get(0).getPastSecond());
    assertEquals(327, rangeStats.get(0).getTotal());
    assertEquals("Widget:37:12", rangeStats.get(0).getDescription());

    assertEquals(PerfReportKind.rebuild, rangeStats.get(1).getKind());
    assertEquals(48, rangeStats.get(1).getPastSecond());
    assertEquals(328, rangeStats.get(1).getTotal());
    assertEquals("Widget:37:12", rangeStats.get(1).getDescription());


    rangeStats = Lists.newArrayList(stats.getRangeStats(locations.get(5)));

    assertEquals(2, rangeStats.size());
    assertEquals(PerfReportKind.repaint, rangeStats.get(0).getKind());
    assertEquals(282, rangeStats.get(0).getPastSecond());
    assertEquals(1962, rangeStats.get(0).getTotal());
    assertEquals("Widget:87:17", rangeStats.get(0).getDescription());

    assertEquals(PerfReportKind.rebuild, rangeStats.get(1).getKind());
    assertEquals(288, rangeStats.get(1).getPastSecond());
    assertEquals(1968, rangeStats.get(1).getTotal());
    assertEquals("Widget:87:17", rangeStats.get(1).getDescription());

    assertFalse(clockModel.isDisposed);
    assertFalse(mainModel.isDisposed);

    flutterWidgetPerf.showFor(new HashSet<>());

    assertTrue(clockModel.isDisposed);
    assertTrue(mainModel.isDisposed);
  }
}

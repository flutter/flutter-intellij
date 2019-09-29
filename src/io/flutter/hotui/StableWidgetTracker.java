/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.hotui;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.inspector.InspectorService;
import io.flutter.preview.OutlineOffsetConverter;
import io.flutter.utils.EventStream;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class that uses the FlutterOutline to maintain the source location for a
 * Widget even when code edits that would otherwise confuse location tracking
 * occur.
 */
public class StableWidgetTracker implements Disposable {
  private final String currentFilePath;
  private final InspectorService.Location initialLocation;
  private final FlutterDartAnalysisServer flutterAnalysisServer;
  private final DartAnalysisServerService analysisServerService;

  private final OutlineOffsetConverter converter;
  // Path to the current outline
  private ArrayList<FlutterOutline> lastPath;

  FlutterOutline root;

  private final FlutterOutlineListener outlineListener = new FlutterOutlineListener() {
    @Override
    public void outlineUpdated(@NotNull String filePath, @NotNull FlutterOutline outline, @Nullable String instrumentedCode) {
      if (Objects.equals(currentFilePath, filePath)) {
        ApplicationManager.getApplication().invokeLater(() -> outlineChanged(outline));
      }
    }
  };

  private void outlineChanged(FlutterOutline outline) {
    this.root = outline;
    FlutterOutline match;
    if (lastPath == null) {
       // First outline.
       lastPath = new ArrayList<>();
       findOutlineAtOffset(root, initialLocation.getOffset(), lastPath);
    } else {
      lastPath = findSimilarPath(root, lastPath);
    }
    currentOutlines.setValue(lastPath.isEmpty() ? ImmutableList.of() : ImmutableList.of(lastPath.get(lastPath.size() - 1)));
  }

  private static int findChildIndex(FlutterOutline node, FlutterOutline child) {
    final List<FlutterOutline> children = node.getChildren();
    for (int i = 0; i < children.size(); i++) {
      if (children.get(i) == child) return i;
    }
    return -1;
  }

  private ArrayList<FlutterOutline> findSimilarPath(FlutterOutline root, ArrayList<FlutterOutline> lastPath) {
    final ArrayList<FlutterOutline> path = new ArrayList<>();
    FlutterOutline node = root;
    path.add(node);
    int i = 1;
    while (i < lastPath.size() && node != null && !node.getChildren().isEmpty()) {
      FlutterOutline oldChild = lastPath.get(i);
      final int expectedIndex = findChildIndex(lastPath.get(i-1), oldChild);
      assert(expectedIndex != -1);
      final List<FlutterOutline> children = node.getChildren();
      final int index = Math.min(Math.max(0, expectedIndex), children.size());
      node = children.get(index);
      if (!Objects.equals(node.getClassName(), oldChild.getClassName()) && node.getChildren().size() == 1) {
        final FlutterOutline child = node.getChildren().get(0);
        if (Objects.equals(child.getClassName(), oldChild.getClassName())) {
          // We have detected that the previous widget was wrapped by a new widget.
          // Add the wrapping widget to the path and otherwise proceed normally.
          path.add(node);
          node = child;
        }
      }
      // TODO(jacobr): consider doing some additional validation that the children have the same class names, etc.
      // We could use that to be reslient to small changes such as adding a new parent widget, etc.
      path.add(node);
      i++;
    }
    return path;
  }

  private boolean findOutlineAtOffset(FlutterOutline outline, int offset, ArrayList<FlutterOutline> path) {
    if (outline == null) {
      return false;
    }
    path.add(outline);
    if (converter.getConvertedOutlineOffset(outline) <= offset && offset <= converter.getConvertedOutlineEnd(outline)) {
      if (outline.getChildren() != null) {
        for (FlutterOutline child : outline.getChildren()) {
          final boolean foundChild = findOutlineAtOffset(child, offset, path);
          if (foundChild) {
            return true;
          }
        }
      }
      return true;
    }
    path.remove(path.size() - 1);
    return false;
  }

  private final EventStream<List<FlutterOutline>> currentOutlines;

  public EventStream<List<FlutterOutline>> getCurrentOutlines() { return currentOutlines; }

  public StableWidgetTracker(
    InspectorService.Location initialLocation,
    FlutterDartAnalysisServer flutterAnalysisServer,
    Project project,
    Disposable parentDisposable
  ) {
    Disposer.register(parentDisposable, this);
    converter = new OutlineOffsetConverter(project, initialLocation.getFile());
    currentOutlines = new EventStream<>(ImmutableList.of());
    this.flutterAnalysisServer = flutterAnalysisServer;
    this.initialLocation = initialLocation;
    analysisServerService = DartAnalysisServerService.getInstance(project);
    currentFilePath = FileUtil.toSystemDependentName(initialLocation.getFile().getPath());
    flutterAnalysisServer.addOutlineListener(currentFilePath, outlineListener);
  }

  @Override
  public void dispose() {
    flutterAnalysisServer.removeOutlineListener(currentFilePath, outlineListener);
  }

  public boolean isValid() {
    return !getCurrentOutlines().getValue().isEmpty();
  }

  public int getOffset() {
    final List<FlutterOutline> outlines = getCurrentOutlines().getValue();
    if (outlines.isEmpty()) return 0;
    final FlutterOutline outline = outlines.get(0);
    return converter.getConvertedOutlineOffset(outline);
  }
}

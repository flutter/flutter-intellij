/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorStateService;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Context with references to data useful for widget editing code.
 */
public class WidgetEditingContext {
  public final Project project;
  public final @Nullable Document document;
  public final FlutterDartAnalysisServer flutterDartAnalysisService;
  public final InspectorStateService inspectorStateService;
  public final EditorPositionService editorPositionService;
  public final @Nullable  EditorEx editor;
  public final @Nullable Component component;

  public WidgetEditingContext(
    @NotNull Document document,
    FlutterDartAnalysisServer flutterDartAnalysisService,
    InspectorStateService inspectorStateService,
    EditorPositionService editorPositionService,
    @NotNull EditorEx editor
  ) {
    this.project = editor.getProject();
    this.document = document;
    this.flutterDartAnalysisService = flutterDartAnalysisService;
    this.inspectorStateService = inspectorStateService;
    this.editorPositionService = editorPositionService;
    this.editor = editor;
    this.component = null;
  }

  public WidgetEditingContext(
    Project project,
    FlutterDartAnalysisServer flutterDartAnalysisService,
    InspectorStateService inspectorStateService,
    EditorPositionService editorPositionService,
    @Nullable Component component
  ) {
    this.project = project;
    this.document = null;
    this.flutterDartAnalysisService = flutterDartAnalysisService;
    this.inspectorStateService = inspectorStateService;
    this.editorPositionService = editorPositionService;
    this.editor = null;
    this.component = component;
  }
}

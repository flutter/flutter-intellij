/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.project.Project;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.InspectorGroupManagerService;

/**
 * Context with references to data useful for widget editing code.
 */
public class WidgetEditingContext {
  public final Project project;
  public final FlutterDartAnalysisServer flutterDartAnalysisService;
  public final InspectorGroupManagerService inspectorGroupManagerService;
  public final EditorPositionService editorPositionService;

  public WidgetEditingContext(
    Project project,
    FlutterDartAnalysisServer flutterDartAnalysisService,
    InspectorGroupManagerService inspectorGroupManagerService,
    EditorPositionService editorPositionService
  ) {
    this.project = project;
    this.flutterDartAnalysisService = flutterDartAnalysisService;
    this.inspectorGroupManagerService = inspectorGroupManagerService;
    this.editorPositionService = editorPositionService;
  }
}

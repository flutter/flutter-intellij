/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import org.dartlang.analysis.server.protocol.FlutterOutline;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data describing widget indents for an editor that is persisted across
 * multiple runs of the WidgetIndentsHighlightingPass.
 */
public class WidgetIndentsPassData {
  public PreviewsForEditor previewsForEditor;
  /**
   * Descriptors describing the data model to render the widget indents.
   * <p>
   * This data is computed from the FlutterOutline and contains additional
   * information to manage how the locations need to be updated to reflect
   * edits to the documents.
   */
  java.util.List<WidgetIndentGuideDescriptor> myDescriptors = Collections.emptyList();

  /**
   * Descriptors combined with their current locations in the possibly modified document.
   */
  java.util.List<TextRangeDescriptorPair> myRangesWidgets = Collections.emptyList();

  /**
   * Highlighters that perform the actual rendering of the widget indent
   * guides.
   */
  List<RangeHighlighter> highlighters;

  /// XXX remove
  // List<RangeHighlighter> propertyHighlighters;

  /**
   * Source of truth for whether other UI overlaps with the widget indents.
   */
  WidgetIndentHitTester hitTester;

  /**
   * Outline the widget indents are based on.
   */
  FlutterOutline outline;
}

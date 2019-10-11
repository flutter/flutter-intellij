/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.Document;

import java.util.ArrayList;

/**
 * Analog to the IndentGuideDescriptor class from the regular FilteredIndentsHighlightingPass.
 * <p>
 * The core difference relative to IndentGuideDescriptor is this descriptor
 * tracks a list of child nodes to visualize the tree structure of a build
 * method. WidgetIndentsHighlightingPass will use this information to draw horizontal
 * lines to show part-child relationships.
 * <p>
 * Widget indent guides depend on the analysis service as the source of truth,
 * so more information has to be still accurate even after the document is
 * edited as there will be a slight delay before new analysis data is available.
 */
public class WidgetIndentGuideDescriptor {
  public final WidgetIndentGuideDescriptor parent;
  public final ArrayList<OutlineLocation> childLines;
  public final OutlineLocation widget;
  public final int indentLevel;
  public final int startLine;
  public final int endLine;

  public WidgetIndentGuideDescriptor(WidgetIndentGuideDescriptor parent,
                                     int indentLevel,
                                     int startLine,
                                     int endLine,
                                     ArrayList<OutlineLocation> childLines,
                                     OutlineLocation widget) {
    this.parent = parent;
    this.childLines = childLines;
    this.widget = widget;
    this.indentLevel = indentLevel;
    this.startLine = startLine;
    this.endLine = endLine;
  }

  void dispose() {
    if (widget != null) {
      widget.dispose();
    }
    if (childLines == null) return;
    for (OutlineLocation childLine : childLines) {
      childLine.dispose();
    }
    childLines.clear();
  }

  /**
   * This method must be called to opt the indent guide into tracking
   * location changes due to document edits.
   * <p>
   * If trackLocations is called on a descriptor, you must later call dispose
   * to stop listening for changes to the document once the descriptor is
   * obsolete.
   */
  public void trackLocations(Document document) {
    if (widget != null) {
      widget.track(document);
    }
    if (childLines == null) return;
    for (OutlineLocation childLine : childLines) {
      childLine.track(document);
    }
  }

  @Override
  public int hashCode() {
    int result = indentLevel;
    result = 31 * result + startLine;
    result = 31 * result + endLine;
    if (childLines != null) {
      for (OutlineLocation location : childLines) {
        result = 31 * result + location.hashCode();
      }
    }
    if (widget != null) {
      result = 31 * result + widget.hashCode();
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final WidgetIndentGuideDescriptor that = (WidgetIndentGuideDescriptor)o;

    if (endLine != that.endLine) return false;
    if (indentLevel != that.indentLevel) return false;
    if (startLine != that.startLine) return false;

    if (childLines == null || that.childLines == null) {
      return childLines == that.childLines;
    }

    if (childLines.size() != that.childLines.size()) {
      return false;
    }

    for (int i = 0; i < childLines.size(); ++i) {
      if (childLines.get(i).equals(that.childLines.get(i))) {
        return false;
      }
    }

    return true;
  }

  public int compareTo(WidgetIndentGuideDescriptor that) {
    int answer = endLine - that.endLine;
    if (answer != 0) {
      return answer;
    }
    answer = indentLevel - that.indentLevel;
    if (answer != 0) {
      return answer;
    }
    answer = startLine - that.startLine;
    if (answer != 0) {
      return answer;
    }

    if (childLines == that.childLines) {
      return 0;
    }

    if (childLines == null || that.childLines == null) {
      return childLines == null ? -1 : 1;
    }

    answer = childLines.size() - that.childLines.size();

    if (answer != 0) {
      return answer;
    }

    for (int i = 0; i < childLines.size(); ++i) {
      answer = childLines.get(i).compareTo(that.childLines.get(i));
      if (answer != 0) {
        return answer;
      }
    }
    return 0;
  }
}

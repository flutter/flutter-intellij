/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterOutlineAttribute;
import org.dartlang.analysis.server.protocol.Location;

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
  public static class WidgetPropertyDescriptor {
    private RangeMarker marker;
    private final FlutterOutlineAttribute attribute;

    WidgetPropertyDescriptor(FlutterOutlineAttribute attribute) {
      this.attribute = attribute;
    }

    public String getName() { return attribute.getName();}

    public FlutterOutlineAttribute getAttribute() {
      return attribute;
    }

    public int getEndOffset() {
      if (marker == null) {
        final Location location = attribute.getValueLocation();
        final int startOffset = attribute.getValueLocation().getOffset();
        final int endOffset = startOffset + location.getLength();
        return endOffset;
      }
      return marker.getEndOffset();
    }

    public void track(Document document) {
      if (marker != null) {
        // TODO(jacobr): it does indicate a bit of a logic bug if we are calling this method twice.
        assert (marker.getDocument() == document);
        return;
      }

      // Create a range marker that goes from the start of the indent for the line
      // to the column of the actual entity.
      final int docLength = document.getTextLength();
      final Location location = attribute.getValueLocation();
      final int startOffset = Math.min(attribute.getValueLocation().getOffset(), docLength);
      final int endOffset = Math.min(startOffset + location.getLength(), docLength);

      marker = document.createRangeMarker(startOffset, endOffset);
//      nodeStartingWord = OutlineLocation.getCurrentWord(document, nameExpression);
    }

    public void dispose() {
      if (marker != null) {
        marker.dispose();
      }
    }
  }

  public final WidgetIndentGuideDescriptor parent;
  public final ArrayList<OutlineLocation> childLines;
  public final OutlineLocation widget;
  public final int indentLevel;
  public final int startLine;
  public final int endLine;

  public final ArrayList<WidgetPropertyDescriptor> properties;
  public final FlutterOutline outlineNode;

  public WidgetIndentGuideDescriptor(
    WidgetIndentGuideDescriptor parent,
    int indentLevel,
    int startLine,
    int endLine,
    ArrayList<OutlineLocation> childLines,
    OutlineLocation widget,
    ArrayList<WidgetPropertyDescriptor> properties,
    FlutterOutline outlineNode
  ) {
    this.parent = parent;
    this.childLines = childLines;
    this.widget = widget;
    this.indentLevel = indentLevel;
    this.startLine = startLine;
    this.endLine = endLine;
    this.properties = properties;
    this.outlineNode =  outlineNode;
  }

  void dispose() {
    if (widget != null) {
      widget.dispose();
    }
    if (childLines == null) return;
    for (OutlineLocation childLine : childLines) {
      childLine.dispose();
    }
    for (WidgetPropertyDescriptor property : properties) {
      property.dispose();
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
  boolean tracked = false;
  public void trackLocations(Document document) {

    if (tracked) return;
    tracked = true;
    if (widget != null) {
      widget.track(document);
    }
    if (childLines == null) return;
    for (OutlineLocation childLine : childLines) {
      childLine.track(document);
    }
    for (WidgetPropertyDescriptor property : properties) {
      property.track(document);
    }
  }

  public TextRange getMarker() {
    return widget.getFullRange();
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
    // XXX add properties.
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
    // XXX add properties.

    for (int i = 0; i < childLines.size(); ++i) {
      if (!childLines.get(i).equals(that.childLines.get(i))) {
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

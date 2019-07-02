/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.Nullable;

/**
 * Class that tracks the location of a FlutterOutline node in a document.
 * <p>
 * Once the track method has been called, edits to the document are reflected
 * by by all locations returned by the outline location.
 */
public class OutlineLocation implements Comparable<OutlineLocation> {
  private final int line;
  private final int column;
  private final int indent;
  private final int offset;
  private final int endOffset;
  @Nullable
  private RangeMarker marker;
  @Nullable
  private String nodeStartingWord;

  /**
   * Get the next word in the document starting at offset.
   * <p>
   * This helper is used to avoid displaying outline guides where it appears
   * that the word at the start of the outline (e.g. the Widget constructor
   * name) has changed since the guide was created. This catches edge cases
   * where RangeMarkers go off the rails and return strange values after
   * running a code formatter or other tool that generates widespread edits.
   */
  private static String getCurrentWord(Document document, int offset) {
    final int documentLength = document.getTextLength();
    if (offset < 0 || offset >= documentLength) return "";
    final CharSequence chars = document.getCharsSequence();
    // Clamp the max current word length at 20 to avoid slow behavior if the
    // next "word" in the document happened to be incredibly long.
    final int maxWordEnd = Math.min(documentLength, offset + 20);

    int end = offset;
    while (end < maxWordEnd && Character.isAlphabetic(chars.charAt(end))) {
      end++;
    }
    if (offset == end) return "";
    return chars.subSequence(offset, end).toString();
  }

  public OutlineLocation(
    FlutterOutline node,
    int line,
    int column,
    int indent,
    VirtualFile file,
    WidgetIndentsHighlightingPass pass
  ) {
    this.line = line;
    this.column = column;
    // These asserts catch cases where the outline is based on inconsistent
    // state with the document.
    // TODO(jacobr): tweak values so if these errors occur they will not
    // cause exceptions to be thrown in release mode.
    assert (indent >= 0);
    assert (column >= 0);
    // It makes no sense for the indent of the line to be greater than the
    // indent of the actual widget.
    assert (column >= indent);
    assert (line >= 0);
    this.indent = indent;
    this.offset = pass.getConvertedOffset(node);
    this.endOffset = pass.getConvertedOffset(node.getOffset() + node.getLength());
  }

  public void dispose() {
    if (marker != null) {
      marker.dispose();
    }
    marker = null;
  }

  /**
   * This method must be called if the location is set to update to reflect
   * edits to the document.
   * <p>
   * This method must be called at most once and if it is called, dispose must
   * also be called to ensure the range marker is disposed.
   */
  public void track(Document document) {
    if (marker != null) {
      // TODO(jacobr): it does indicate a bit of a logic bug if we are calling this method twice.
      assert (marker.getDocument() == document);
      return;
    }

    assert (indent <= column);

    final int delta = Math.max(column - indent, 0);
    final int markerEnd = offset;

    // Create a range marker that goes from the start of the indent for the line
    // to the column of the actual entity.
    final int docLength = document.getTextLength();
    int startOffset = Math.max(markerEnd - delta, 0);
    startOffset = Math.min(startOffset, docLength);
    final int endOffset = Math.min(markerEnd + 1, docLength);

    marker = document.createRangeMarker(startOffset, endOffset);
    nodeStartingWord = getCurrentWord(document, markerEnd);
  }

  @Override
  public int hashCode() {
    int hashCode = line;
    hashCode = hashCode * 31 + column;
    hashCode = hashCode * 31 + indent;
    hashCode = hashCode * 31 + offset;
    return hashCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OutlineLocation)) return false;
    final OutlineLocation other = (OutlineLocation)o;
    return line == other.line &&
           column == other.column &&
           indent == other.indent &&
           offset == other.offset &&
           getOffset() == other.getOffset();
  }

  /**
   * Offset in the document accurate even if the document has been edited.
   */
  public int getOffset() {
    return marker == null ? offset : marker.getStartOffset();
  }

  // Sometimes markers stop being valid in which case we need to stop
  // displaying the rendering until they are valid again.
  public boolean isValid() {
    if (marker == null) return true;

    return marker.isValid() &&
           nodeStartingWord != null &&
           // Verify that the word starting at the end of the marker matches
           // its expected value. This is sometimes not the case if the logic
           // to update marker locations has hit a bad edge case as sometimes
           // happens when there is a large document edit due to running a
           // code formatter.
           nodeStartingWord.equals(getCurrentWord(marker.getDocument(), marker.getEndOffset() - 1));
  }

  /**
   * Line in the document this outline node is at.
   */
  public int getLine() {
    return marker == null ? line : marker.getDocument().getLineNumber(marker.getStartOffset());
  }

  private int getColumnForOffset(int offset) {
    assert (marker != null);
    final Document document = marker.getDocument();
    final int currentLine = document.getLineNumber(offset);
    return offset - document.getLineStartOffset(currentLine);
  }

  /*
   * Indent of the line to use for line visualization.
   *
   * This may intentionally differ from the column as for the line
   * `  child: Text(`
   * The indent will be 2 while the column is 9.
   */
  public int getIndent() {
    return marker == null ? indent : getColumnForOffset(marker.getStartOffset());
  }

  /**
   * Column this outline node is at.
   * <p>
   * This is the column offset of the start of the widget constructor call.
   */
  public int getColumn() {
    return marker == null ? column : getColumnForOffset(Math.max(marker.getStartOffset(), marker.getEndOffset() - 1));
  }

  public TextRange getTextRange() {
    return marker == null ? new TextRange(offset, endOffset) : new TextRange(marker.getStartOffset(), marker.getEndOffset());
  }

  @Override
  public int compareTo(OutlineLocation o) {
    // We use the initial location of the outline location when performing
    // comparisons rather than the current location for efficiency
    // and stability.
    int delta = Integer.compare(line, o.line);
    if (delta != 0) return delta;
    delta = Integer.compare(column, o.column);
    if (delta != 0) return delta;
    return Integer.compare(indent, o.indent);
  }
}

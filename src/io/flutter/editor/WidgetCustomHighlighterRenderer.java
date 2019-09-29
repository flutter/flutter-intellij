/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorStateService;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.FlutterOutlineAttribute;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

public class WidgetCustomHighlighterRenderer extends WidgetViewController
  implements CustomHighlighterRenderer, EditorPositionService.Listener, Disposable {

  private final InspectorStateService inspectorStateService;
  private final EditorMouseEventService editorEventService;

  WidgetCustomHighlighterRenderer(WidgetViewModelData data, Project project) {
    super(data);
    inspectorStateService = InspectorStateService.getInstance(project);
    inspectorStateService.addListener(this);
    editorEventService = EditorMouseEventService.getInstance(project);
    editorEventService.addListener( data.context.editor, this);
  }

  @Override
  public void onVisibleChanged() {

  }

  @Override
  public void dispose() {
    editorEventService.removeListener(data.context.editor, this);
    inspectorStateService.removeListener(this);
    data.descriptor.dispose();
  }

  @Override
  public void onMouseMoved(MouseEvent event) {

  }

  @Override
  public void onMousePressed(MouseEvent event) {

  }

  @Override
  public void onMouseReleased(MouseEvent event) {

  }

  @Override
  public void onMouseEntered(MouseEvent event) {

  }

  @Override
  public void onMouseExited(MouseEvent event) {

  }

  @Override
  public void updateSelected(Caret carat) {
    if (updateSelectedHelper(carat)) {
      forceRender();
    }
  }

  @Override
  public void onSelectionChanged(DiagnosticsNode selection) {

  }

  @Override
  public void onInspectorAvailable(InspectorService service) {

  }

  @Override
  public void requestRepaint(boolean force) {

  }

  @Override
  public void onFlutterFrame() {

  }

  boolean updateSelectedHelper(Caret carat) {
    if (carat == null) {
      return setSelection(false);
    }
    final CaretModel caretModel = data.context.editor.getCaretModel();

    final TextRange marker = data.getMarker();
    if (marker == null) return false;

    final int startOffset = marker.getStartOffset();
    final Document doc = data.context.document;
    final int caretOffset = carat.getOffset();

    if (startOffset >= doc.getTextLength()) {
      return setSelection(false);
    }

    final int endOffset = marker.getEndOffset();

    int off = startOffset;
    int startLine = doc.getLineNumber(startOffset);
    {
      final CharSequence chars = doc.getCharsSequence();
      do {
        final int start = doc.getLineStartOffset(startLine);
        final int end = doc.getLineEndOffset(startLine);
        off = CharArrayUtil.shiftForward(chars, start, end, " \t");
        startLine--;
      }
      while (startLine > 1 && off < doc.getTextLength() && chars.charAt(off) == '\n');
    }

    final VisualPosition startPosition = data.context.editor.offsetToVisualPosition(off);
    final int indentColumn = startPosition.column;

    final LogicalPosition logicalPosition = caretModel.getLogicalPosition();
    if (logicalPosition.line == startLine + 1 && getDescriptor().widget != null) {
      // Be more permissive about what constitutes selection for the first
      // line within a widget constructor.
      return setSelection(caretModel.getLogicalPosition().column >= indentColumn);
    }
    return setSelection(
      caretOffset >= off && caretOffset < endOffset && caretModel.getLogicalPosition().column == indentColumn);
  }

  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
    if (!highlighter.isValid()) {
      return;
    }
    if (!getDescriptor().widget.isValid()) {
      return;
    }
    final FlutterSettings settings = FlutterSettings.getInstance();
    final boolean showMultipleChildrenGuides = settings.isShowMultipleChildrenGuides();

    final Graphics2D g2d = (Graphics2D)g.create();
    // Required to render colors with an alpha channel. Rendering with an
    // alpha chanel makes it easier to keep relationships between shadows
    // and lines looking consistent when the background color changes such
    // as in the case of selection or a different highlighter turning the
    // background yellow.
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1));

    final int startOffset = highlighter.getStartOffset();
    final Document doc = highlighter.getDocument();
    final int textLength = doc.getTextLength();
    if (startOffset >= textLength) return;

    final int endOffset = min(highlighter.getEndOffset(), textLength);

    int off;
    int startLine = doc.getLineNumber(startOffset);
    final int lineHeight = editor.getLineHeight();
    final Rectangle clip = g2d.getClipBounds();


    final WidgetIndentGuideDescriptor descriptor = getDescriptor();
    if (descriptor != null && descriptor.properties != null && !descriptor.properties.isEmpty()){
      if (visibleRect != null) {
        Rectangle safeClip = new Rectangle(clip);
        safeClip.width = max(visibleRect.width - 280, 0);
        g2d.clip(safeClip);
      }
      final Font font = UIUtil.getFont(UIUtil.FontSize.NORMAL, UIUtil.getTreeFont());
      g2d.setFont(font);

      for (WidgetIndentGuideDescriptor.WidgetPropertyDescriptor property : descriptor.properties) {
        final int propertyEndOffset = property.getEndOffset();
        final int propertyLine = doc.getLineNumber(propertyEndOffset);
        final int lineEndOffset = doc.getLineEndOffset(propertyLine);

        VisualPosition visualPosition = editor.offsetToVisualPosition(lineEndOffset); // e
        visualPosition = new VisualPosition(visualPosition.line, max(visualPosition.column + 1, 4));
        // final VisualPosition startPosition = editor.offsetToVisualPosition(off);
        final Point start = editor.visualPositionToXY(visualPosition);
        if (start.y + lineHeight > clip.y && start.y < clip.y + clip.height) {

          String text;
          String value;
          FlutterOutlineAttribute attribute = property.getAttribute();
          boolean constValue = false;
          if (attribute.getLiteralValueBoolean() != null) {
            value = attribute.getLiteralValueBoolean().toString();
            constValue = true;
          }
          else if (attribute.getLiteralValueInteger() != null) {
            value = attribute.getLiteralValueInteger().toString();
            constValue = true;
          }
          else if (attribute.getLiteralValueString() != null) {
            value = '"' + attribute.getLiteralValueString() + '"';
            constValue = true;
          }
          else {
            value = attribute.getLabel();
            if (value == null) {
              value = "<loading value>";
            }
          }
          if (property.getName().equals("data")) {
            text = value;
          }
          else {
            text = property.getName() + ": " + value;
          }

          // TODO(jacobr): detect other const like things and hide them.
          if (!constValue)
          {
           //  final float width = computeStringWidth(editor, text, font);
            //          g2d.setColor(JBColor.LIGHT_GRAY);
            //        g2d.fillRect(start.x, start.y, (int)width + 8, lineHeight);
            g2d.setColor(FlutterEditorColors.SHADOW_GRAY);
            g2d.drawString(text, start.x + 4, start.y + lineHeight - 4);
          }
        }
      }
      g2d.setClip(clip);
    }


    final CharSequence chars = doc.getCharsSequence();
    do {
      final int start = doc.getLineStartOffset(startLine);
      final int end = doc.getLineEndOffset(startLine);
      off = CharArrayUtil.shiftForward(chars, start, end, " \t");
      startLine--;
    }
    while (startLine > 1 && off < doc.getTextLength() && chars.charAt(off) == '\n');

    final VisualPosition startPosition = editor.offsetToVisualPosition(off);
    int indentColumn = startPosition.column;

    // It's considered that indent guide can cross not only white space but comments, javadoc etc. Hence, there is a possible
    // case that the first indent guide line is, say, single-line comment where comment symbols ('//') are located at the first
    // visual column. We need to calculate correct indent guide column then.
    int lineShift = 1;
    if (indentColumn <= 0 && descriptor != null) {
      indentColumn = descriptor.indentLevel;
      lineShift = 0;
    }
    if (indentColumn <= 0) return;

    final FoldingModel foldingModel = editor.getFoldingModel();
    if (foldingModel.isOffsetCollapsed(off)) return;

    final FoldRegion headerRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineEndOffset(doc.getLineNumber(off)));
    final FoldRegion tailRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineStartOffset(doc.getLineNumber(endOffset)));

    if (tailRegion != null && tailRegion == headerRegion) return;

    final CaretModel caretModel = editor.getCaretModel();
    final int caretOffset = caretModel.getOffset();
    //      updateSelected(editor, highlighter, caretOffset);
    final boolean selected = isSelected;

    final Point start = editor.visualPositionToXY(new VisualPosition(startPosition.line + lineShift, indentColumn));

    final VisualPosition endPosition = editor.offsetToVisualPosition(endOffset);
    final ArrayList<OutlineLocation> childLines = descriptor.childLines;
    final Point end = editor.visualPositionToXY(endPosition);
    double splitY = -1;
    int maxY = end.y;
    boolean includeLastLine = false;
    if (endPosition.line == editor.offsetToVisualPosition(doc.getTextLength()).line) {
      includeLastLine = true;
    }

    int endLine = doc.getLineNumber(endOffset);
    if (childLines != null && childLines.size() > 0) {
      final VisualPosition endPositionLastChild = editor.offsetToVisualPosition(childLines.get(childLines.size() - 1).getGuideOffset());
      if (endPositionLastChild.line == endPosition.line) {
        // The last child is on the same line as the end of the block.
        // This happens if code wasn't formatted with flutter style, for example:
        //  Center(
        //    child: child);

        includeLastLine = true;
        // TODO(jacobr): make sure we don't run off the edge of the document.
        if ((endLine + 1) < data.context.document.getLineCount()) {
          endLine++;
        }
      }
    }
    // By default we stop at the start of the last line instead of the end of the last line in the range.
    if (includeLastLine) {
      maxY += lineHeight;
    }

    if (clip != null) {
      if (clip.y > maxY || clip.y + clip.height < start.y) {
        return;
      }
      maxY = min(maxY, clip.y + clip.height);
    }

    final EditorColorsScheme scheme = editor.getColorsScheme();
    final JBColor lineColor = selected ? JBColor.BLUE : FlutterEditorColors.OUTLINE_LINE_COLOR;
    g2d.setColor(lineColor);
    final Color pastBlockColor = selected ? scheme.getColor(EditorColors.SELECTED_INDENT_GUIDE_COLOR) : FlutterEditorColors.OUTLINE_LINE_COLOR_PAST_BLOCK;

    // TODO(jacobr): this logic for softwraps is duplicated for the FilteredIndentsHighlightingPass
    // and may be more conservative than sensible for WidgetIndents.

    // There is a possible case that indent line intersects soft wrap-introduced text. Example:
    //     this is a long line <soft-wrap>
    // that| is soft-wrapped
    //     |
    //     | <- vertical indent
    //
    // Also it's possible that no additional intersections are added because of soft wrap:
    //     this is a long line <soft-wrap>
    //     |   that is soft-wrapped
    //     |
    //     | <- vertical indent
    // We want to use the following approach then:
    //     1. Show only active indent if it crosses soft wrap-introduced text;
    //     2. Show indent as is if it doesn't intersect with soft wrap-introduced text;

    int y = start.y;
    int newY = start.y;
    final int maxYWithChildren = y;
    final SoftWrapModel softWrapModel = editor.getSoftWrapModel();
    int iChildLine = 0;
    for (int i = max(0, startLine + lineShift); i < endLine && newY < maxY; i++) {
      OutlineLocation childLine = null;
      if (childLines != null) {
        while (iChildLine < childLines.size()) {
          final OutlineLocation currentChildLine = childLines.get(iChildLine);
          if (currentChildLine.isValid()) {
            if (currentChildLine.getLine() > i) {
              // We haven't reached child line yet.
              break;
            }
            if (currentChildLine.getLine() == i) {
              childLine = currentChildLine;
              iChildLine++;
              if (iChildLine >= childLines.size()) {
                splitY = newY + (lineHeight * 0.5);
              }
              break;
            }
          }
          iChildLine++;
        }

        if (childLine != null) {
          final int childIndent = childLine.getIndent();
          // Draw horizontal line to the child.
          final VisualPosition widgetVisualPosition = editor.offsetToVisualPosition(childLine.getGuideOffset());
          final Point widgetPoint = editor.visualPositionToXY(widgetVisualPosition);
          final int deltaX = widgetPoint.x - start.x;
          // We add a larger amount of panding at the end of the line if the indent is larger up until a max of 6 pixels which is the max
          // amount that looks reasonable. We could remove this and always used a fixed padding.
          final int padding = max(min(abs(deltaX) / 3, 6), 2);
          if (deltaX > 0) {
            // This is the normal case where we draw a foward line to the connected child.
            LinePainter2D.paint(
              g2d,
              start.x + 2,
              newY + lineHeight * 0.5,
              //start.x + charWidth  * childIndent - padding,
              widgetPoint.x - padding,
              newY + lineHeight * 0.5
            );
          }
          else {
            // Edge case where we draw a backwards line to clarify
            // that the node is still a child even though the line is in
            // the wrong direction. This is mainly for debugging but could help
            // users fix broken UI.
            // We draw this line so it is inbetween the lines of text so it
            // doesn't get in the way.
            final int loopBackLength = 6;

            //              int endX = start.x + charWidth  * (childIndent -1) - padding - loopBackLength;
            final int endX = widgetPoint.x - padding;
            LinePainter2D.paint(
              g2d,
              start.x + 2,
              newY,
              endX,
              newY
            );
            LinePainter2D.paint(
              g2d,
              endX,
              newY,
              endX,
              newY + lineHeight * 0.5
            );
            LinePainter2D.paint(
              g2d,
              endX,
              newY + lineHeight * 0.5,
              endX + loopBackLength,
              newY + lineHeight * 0.5
            );
          }
        }
      }

      final List<? extends SoftWrap> softWraps = softWrapModel.getSoftWrapsForLine(i);
      int logicalLineHeight = softWraps.size() * lineHeight;
      if (i > startLine + lineShift) {
        logicalLineHeight += lineHeight; // We assume that initial 'y' value points just below the target line.
      }
      if (!softWraps.isEmpty() && softWraps.get(0).getIndentInColumns() < indentColumn) {
        if (y < newY || i > startLine + lineShift) { // There is a possible case that soft wrap is located on indent start line.
          drawVerticalLineHelper(g2d, lineColor, start.x, y, newY + lineHeight, childLines,
                                 showMultipleChildrenGuides);
        }
        newY += logicalLineHeight;
        y = newY;
      }
      else {
        newY += logicalLineHeight;
      }

      final FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineEndOffset(i));
      if (foldRegion != null && foldRegion.getEndOffset() < doc.getTextLength()) {
        i = doc.getLineNumber(foldRegion.getEndOffset());
      }
    }

    if (childLines != null && iChildLine < childLines.size() && splitY == -1) {
      // Clipped rectangle is all within the main body.
      splitY = maxY;
    }
    if (y < maxY) {
      if (splitY != -1) {
        drawVerticalLineHelper(g2d, lineColor, start.x, y, splitY, childLines, showMultipleChildrenGuides);
        g2d.setColor(pastBlockColor);
        g2d.drawLine(start.x + 2, (int)splitY + 1, start.x + 2, maxY);
      }
      else {
        g2d.setColor(pastBlockColor);
        g2d.drawLine(start.x + 2, y, start.x + 2, maxY);
      }
    }
    g2d.dispose();
  }

  public static void drawVerticalLineHelper(
    Graphics2D g,
    Color lineColor,
    int x,
    double yStart,
    double yEnd,
    ArrayList<OutlineLocation> childLines,
    boolean showMultipleChildrenGuides
  ) {
    if (childLines != null && childLines.size() >= 2 && showMultipleChildrenGuides) {
      // TODO(jacobr): optimize this code a bit. This is a sloppy way to draw these lines.
      g.setStroke(FlutterEditorColors.SOLID_STROKE);
      g.setColor(lineColor);
      g.drawLine(x + 1, (int)yStart, x + 1, (int)yEnd + 1);
      g.drawLine(x + 2, (int)yStart, x + 2, (int)yEnd + 1);
    }
    else {
      g.setColor(lineColor);
      g.drawLine(x + 2, (int)yStart, x + 2, (int)yEnd + 1);
    }
  }

  public static int compare(@NotNull TextRangeDescriptorPair r, @NotNull RangeHighlighter h) {
    int answer = r.range.getStartOffset() - h.getStartOffset();
    if (answer != 0) {
      return answer;
    }
    answer = r.range.getEndOffset() - h.getEndOffset();
    if (answer != 0) {
      return answer;
    }
    final CustomHighlighterRenderer renderer = h.getCustomRenderer();
    if (renderer instanceof WidgetCustomHighlighterRenderer) {
      final WidgetCustomHighlighterRenderer widgetRenderer = (WidgetCustomHighlighterRenderer)renderer;
      return widgetRenderer.getDescriptor().compareTo(r.descriptor);
    }
    return -1;
  }
}

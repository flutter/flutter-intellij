/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;

import static java.lang.Math.*;

// Instructions for how this code should be tested:
// This code could be tested by true integration tests or better yet by
// unittests that are able to create Editor object instances. Testing this
// code does not require running a Flutter application but it does require
// creating Editor object instances and would benefit from creating a live
// Dart analysis server to communicate with.
//
// Suggested steps to test this code:
// Create a representative Dart file containing a a couple build methods with
// deeply nested widget trees.
// Create an Editor instance from the dart file.
// Get a Flutter outline from that file or inject a snapshotted Flutter outline
// iof it isn't feasible to get a true Flutter outline.
// Verify that calling
// pass = new WidgetIndentsHighlightingPass(project, editor);
// pass.setOutline(flutterOutline);
// results in adding highlights to the expected ranges. Highlighters can be
// found querying the editor directly or calling.

// final CustomHighlighterRenderer renderer = highlighter.getCustomRenderer();
//      ((WidgetCustomHighlighterRenderer)renderer).dispose();
// You could then even call the render method on a highlighter if you wanted
// a golden image that just contained the widget indent tree diagram. In
// practice it would be sufficient to get the WidgetIndentGuideDescriptor from
// the renderer and verify that the child locations are correct. The
// important piece to test is that the child widget locations are acurate even
// after making some edits to the document which brings to the next step:
// Make a couple edits to the document and verify that the widget indents are
// still accurate even after the change. The machinery in Editor will track the
// edits and update the widget indents appropriately even before a new
// FlutterOutline is available.
//
// Final step: create a new FlutterOutline and verify passing it in updates the
// widget guides removing guides not part of the outline. For example, Add a
// character to a constructor name so the constructor is not a Widget subclass.
// That will cause the outermost guide in the tree to be removed. Alternately,
// add another widget to the list of children for a widget.
//
// You could also performa golden image integration test to verify that the
// actual render of the text editor matched what was expected but changes
// in font rendering would make that tricky.

/**
 * A WidgetIndentsHighlightingPass drawsg UI as Code Guides for a code editor using a
 * FlutterOutline.
 * <p>
 * This class is similar to a TextEditorHighlightingPass but doesn't actually
 * implement TextEditorHighlightingPass as it is driven by changes to the
 * FlutterOutline which is only available when the AnalysisServer computes a
 * new outline while TextEditorHighlightingPass assumes all information needed
 * is available immediately.
 */
public class WidgetIndentsHighlightingPass {
  private static final Logger LOG = Logger.getInstance(WidgetIndentsHighlightingPass.class);

  private final static Stroke SOLID_STROKE = new BasicStroke(1);
  private final static JBColor VERY_LIGHT_GRAY = new JBColor(Gray._224, Gray._80);
  private final static JBColor SHADOW_GRAY = new JBColor(Gray._192, Gray._100);
  private final static JBColor OUTLINE_LINE_COLOR = new JBColor(Gray._128, Gray._128);
  private final static JBColor OUTLINE_LINE_COLOR_PAST_BLOCK = new JBColor(new Color(128, 128, 128, 65), new Color(128, 128, 128, 65));
  private final static JBColor BUILD_METHOD_STRIPE_COLOR = new JBColor(new Color(0xc0d8f0), new Color(0x8d7043));

  private static final Key<WidgetIndentsPassData> INDENTS_PASS_DATA_KEY = Key.create("INDENTS_PASS_DATA_KEY");

  /**
   * When this debugging flag is true, problematic text ranges are reported.
   */
  private final static boolean DEBUG_WIDGET_INDENTS = false;

  private static class WidgetCustomHighlighterRenderer implements CustomHighlighterRenderer {
    private final WidgetIndentGuideDescriptor descriptor;
    private final Document document;
    private boolean isSelected = false;

    WidgetCustomHighlighterRenderer(WidgetIndentGuideDescriptor descriptor, Document document) {
      this.descriptor = descriptor;
      this.document = document;
      descriptor.trackLocations(document);
    }

    void dispose() {
      // Descriptors must be disposed so they stop getting notified about
      // changes to the Editor.
      descriptor.dispose();
    }

    boolean setSelection(boolean value) {
      if (value == isSelected) return false;
      isSelected = value;
      return true;
    }

    boolean updateSelected(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, Caret carat) {
      if (carat == null) {
        return setSelection(false);
      }
      final CaretModel caretModel = editor.getCaretModel();
      final int startOffset = highlighter.getStartOffset();
      final Document doc = highlighter.getDocument();
      final int caretOffset = carat.getOffset();

      if (startOffset >= doc.getTextLength()) {
        return setSelection(false);
      }

      final int endOffset = highlighter.getEndOffset();

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

      final VisualPosition startPosition = editor.offsetToVisualPosition(off);
      final int indentColumn = startPosition.column;

      final LogicalPosition logicalPosition = caretModel.getLogicalPosition();
      if (logicalPosition.line == startLine + 1 && descriptor.widget != null) {
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
      if (!descriptor.widget.isValid()) {
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
        final VisualPosition endPositionLastChild = editor.offsetToVisualPosition(childLines.get(childLines.size() - 1).getOffset());
        if (endPositionLastChild.line == endPosition.line) {
          // The last child is on the same line as the end of the block.
          // This happens if code wasn't formatted with flutter style, for example:
          //  Center(
          //    child: child);

          includeLastLine = true;
          // TODO(jacobr): make sure we don't run off the edge of the document.
          if ((endLine + 1) < document.getLineCount()) {
            endLine++;
          }
        }
      }
      // By default we stop at the start of the last line instead of the end of the last line in the range.
      if (includeLastLine) {
        maxY += editor.getLineHeight();
      }

      final Rectangle clip = g2d.getClipBounds();
      if (clip != null) {
        if (clip.y > maxY || clip.y + clip.height < start.y) {
          return;
        }
        maxY = min(maxY, clip.y + clip.height);
      }

      final EditorColorsScheme scheme = editor.getColorsScheme();
      final JBColor lineColor = selected ? JBColor.BLUE : OUTLINE_LINE_COLOR;
      g2d.setColor(lineColor);
      final Color pastBlockColor = selected ? scheme.getColor(EditorColors.SELECTED_INDENT_GUIDE_COLOR) : OUTLINE_LINE_COLOR_PAST_BLOCK;

      // TODO(jacobr): this logic for softwraps is duplicated for the FliteredIndentsHighlightingPass
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
      final int lineHeight = editor.getLineHeight();
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
            final VisualPosition widgetVisualPosition = editor.offsetToVisualPosition(childLine.getOffset());
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
  }

  private final EditorEx myEditor;
  private final Document myDocument;
  private final Project myProject;
  private final VirtualFile myFile;
  private final boolean convertOffsets;

  WidgetIndentsHighlightingPass(@NotNull Project project, @NotNull EditorEx editor, boolean convertOffsets) {
    this.myDocument = editor.getDocument();
    this.myEditor = editor;
    this.myProject = project;
    this.myFile = editor.getVirtualFile();
    this.convertOffsets = convertOffsets;
  }

  private static void drawVerticalLineHelper(
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
      g.setStroke(SOLID_STROKE);
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
      return widgetRenderer.descriptor.compareTo(r.descriptor);
    }
    return -1;
  }

  /**
   * Indent guides are hidden if they overlap with a widget indent guide.
   */
  public static boolean isIndentGuideHidden(@NotNull Editor editor, @NotNull LineRange lineRange) {
    final WidgetIndentsPassData data = getIndentsPassData(editor);
    return data != null && isIndentGuideHidden(data.hitTester, lineRange);
  }

  public static boolean isIndentGuideHidden(WidgetIndentHitTester hitTester, @NotNull LineRange lineRange) {
    return hitTester != null && hitTester.intersects(lineRange);
  }

  public static void onCaretPositionChanged(EditorEx editor, Caret caret) {
    final WidgetIndentsPassData data = getIndentsPassData(editor);
    if (data == null || data.highlighters == null) return;
    for (RangeHighlighter h : data.highlighters) {
      if (h.getCustomRenderer() instanceof WidgetIndentsHighlightingPass.WidgetCustomHighlighterRenderer) {
        final WidgetIndentsHighlightingPass.WidgetCustomHighlighterRenderer renderer =
          (WidgetIndentsHighlightingPass.WidgetCustomHighlighterRenderer)h.getCustomRenderer();
        final boolean changed = renderer.updateSelected(editor, h, caret);
        if (changed) {
          editor.repaint(h.getStartOffset(), h.getEndOffset());
        }
      }
    }
  }

  private static WidgetIndentsPassData getIndentsPassData(Editor editor) {
    return editor.getUserData(INDENTS_PASS_DATA_KEY);
  }

  public static void disposeHighlighter(RangeHighlighter highlighter) {
    final CustomHighlighterRenderer renderer = highlighter.getCustomRenderer();
    if (renderer instanceof WidgetCustomHighlighterRenderer) {
      ((WidgetCustomHighlighterRenderer)renderer).dispose();
    }
    highlighter.dispose();
  }

  public static void cleanupHighlighters(Editor editor) {
    final WidgetIndentsPassData data = getIndentsPassData(editor);
    if (data == null) return;

    final List<RangeHighlighter> oldHighlighters = data.highlighters;
    if (oldHighlighters != null) {
      for (RangeHighlighter highlighter : oldHighlighters) {
        disposeHighlighter(highlighter);
      }
    }
    setIndentsPassData(editor, null);
  }

  public static void run(@NotNull Project project, @NotNull EditorEx editor, @NotNull FlutterOutline outline, boolean convertOffsets) {
    final WidgetIndentsHighlightingPass widgetIndentsHighlightingPass = new WidgetIndentsHighlightingPass(project, editor, convertOffsets);
    widgetIndentsHighlightingPass.setOutline(outline);
  }

  /**
   * This method must be called on the main UI thread.
   * <p>
   * Some of this logic would appear to be safe to call on a background thread but
   * there are race conditions where the data will be out of order if the document
   * is being edited while the code is executing.
   * <p>
   * If there are performance concerns we can work to perform more of this
   * computation on a separate thread.
   */
  public void setOutline(FlutterOutline outline) {
    assert (outline != null);

    final WidgetIndentsPassData data = getIndentsPassData();
    if (data.outline == outline) {
      // The outline has not changed. There is nothing we need to do.
      return;
    }

    final ArrayList<WidgetIndentGuideDescriptor> descriptors = new ArrayList<>();

    buildWidgetDescriptors(descriptors, outline, null);
    updateHitTester(new WidgetIndentHitTester(descriptors, myDocument), data);
    // TODO(jacobr): we need to trigger a rerender of highlighters that will render differently due to the changes in highlighters?
    data.myDescriptors = descriptors;
    doCollectInformationUpdateOutline(data);
    doApplyIndentInformationToEditor(data);
    setIndentsPassData(data);
  }

  private void updateHitTester(WidgetIndentHitTester hitTester, WidgetIndentsPassData data) {
    if (Objects.equals(data.hitTester, hitTester)) {
      return;
    }
    FliteredIndentsHighlightingPass.onWidgetIndentsChanged(myEditor, data.hitTester, hitTester);
    data.hitTester = hitTester;
  }

  private WidgetIndentsPassData getIndentsPassData() {
    WidgetIndentsPassData data = getIndentsPassData(myEditor);
    if (data == null) {
      data = new WidgetIndentsPassData();
    }
    return data;
  }

  static void setIndentsPassData(Editor editor, WidgetIndentsPassData data) {
    editor.putUserData(INDENTS_PASS_DATA_KEY, data);
  }

  void setIndentsPassData(WidgetIndentsPassData data) {
    setIndentsPassData(myEditor, data);
  }

  public void doCollectInformationUpdateOutline(WidgetIndentsPassData data) {
    assert myDocument != null;

    if (data.myDescriptors != null) {
      final ArrayList<TextRangeDescriptorPair> ranges = new ArrayList<>();
      for (WidgetIndentGuideDescriptor descriptor : data.myDescriptors) {
        ProgressManager.checkCanceled();
        final TextRange range;
        if (descriptor.widget != null) {
          range = descriptor.widget.getTextRange();
        }
        else {
          final int endOffset =
            descriptor.endLine < myDocument.getLineCount() ? myDocument.getLineStartOffset(descriptor.endLine) : myDocument.getTextLength();
          range = new TextRange(myDocument.getLineStartOffset(descriptor.startLine), endOffset);
        }
        ranges.add(new TextRangeDescriptorPair(range, descriptor));
      }
      ranges.sort((a, b) -> Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(a.range, b.range));
      data.myRangesWidgets = ranges;
    }
  }

  public void doApplyIndentInformationToEditor(WidgetIndentsPassData data) {
    final MarkupModel mm = myEditor.getMarkupModel();

    final List<RangeHighlighter> oldHighlighters = data.highlighters;
    final List<RangeHighlighter> newHighlighters = new ArrayList<>();

    int curRange = 0;

    final List<TextRangeDescriptorPair> ranges = data.myRangesWidgets;
    if (oldHighlighters != null) {
      // after document change some range highlighters could have become
      // invalid, or the order could have been broken.
      // This is similar to logic in FliteredIndentsHighlightingPass.java that also attempts to
      // only update highlighters that have actually changed.
      oldHighlighters.sort(Comparator.comparing((RangeHighlighter h) -> !h.isValid())
                             .thenComparing(Segment.BY_START_OFFSET_THEN_END_OFFSET));
      int curHighlight = 0;
      // It is fine if we cleanupHighlighters and update some old highlighters that are
      // still valid but it is not ok if we leave even one highlighter that
      // really changed as that will cause rendering artifacts.
      while (curRange < ranges.size() && curHighlight < oldHighlighters.size()) {
        final TextRangeDescriptorPair entry = ranges.get(curRange);
        final RangeHighlighter highlighter = oldHighlighters.get(curHighlight);

        if (!highlighter.isValid()) break;

        final int cmp = compare(entry, highlighter);
        if (cmp < 0) {
          newHighlighters.add(createHighlighter(mm, entry));
          curRange++;
        }
        else if (cmp > 0) {
          disposeHighlighter(highlighter);
          curHighlight++;
        }
        else {
          newHighlighters.add(highlighter);
          curHighlight++;
          curRange++;
        }
      }

      for (; curHighlight < oldHighlighters.size(); curHighlight++) {
        final RangeHighlighter highlighter = oldHighlighters.get(curHighlight);
        if (!highlighter.isValid()) break;
        disposeHighlighter(highlighter);
      }
    }

    final int startRangeIndex = curRange;
    assert myDocument != null;
    DocumentUtil.executeInBulk(myDocument, ranges.size() > 10000, () -> {
      for (int i = startRangeIndex; i < ranges.size(); i++) {
        newHighlighters.add(createHighlighter(mm, ranges.get(i)));
      }
    });

    data.highlighters = newHighlighters;
  }

  DartAnalysisServerService getAnalysisService() {
    // TODO(jacobr): cache this?
    return DartAnalysisServerService.getInstance(myProject);
  }

  /**
   * All calls to convert offsets for indent highlighting must go through this
   * method.
   *
   * Sometimes we need to use the raw offsets and sometimes we need
   * to use the converted offsets depending on whether the FlutterOutline
   * matches the current document or the expectations given by the
   * @param node
   * @return
   */
  int getConvertedOffset(FlutterOutline node) {
    return getConvertedOffset(node.getOffset());
  }

  int getConvertedOffset(int offset) {
    return convertOffsets ? getAnalysisService().getConvertedOffset(myFile, offset) : offset;
  }

  private OutlineLocation computeLocation(FlutterOutline node) {
    assert (myDocument != null);
    final int documentLength = myDocument.getTextLength();
    final int rawOffset = getConvertedOffset(node);
    final int nodeOffset = min(rawOffset, documentLength);
    final int line = myDocument.getLineNumber(nodeOffset);
    final int lineStartOffset = myDocument.getLineStartOffset(line);

    final int column = nodeOffset - lineStartOffset;
    int indent = 0;
    final CharSequence chars = myDocument.getCharsSequence();

    // TODO(jacobr): we only really want to include the previous token (e.g.
    // "child: " instead of the entire line). That won't matter much but could
    // lead to slightly better results on code edits.
    for (indent = 0; indent < column; indent++) {
      if (!Character.isWhitespace(chars.charAt(lineStartOffset + indent))) {
        break;
      }
    }

    return new OutlineLocation(node, line, column, indent, myFile, this);
  }

  private void buildWidgetDescriptors(
    final List<WidgetIndentGuideDescriptor> widgetDescriptors,
    FlutterOutline outlineNode,
    WidgetIndentGuideDescriptor parent
  ) {
    if (outlineNode == null) return;

    final String kind = outlineNode.getKind();
    final boolean widgetConstructor = "NEW_INSTANCE".equals(kind);

    final List<FlutterOutline> children = outlineNode.getChildren();
    if (children == null || children.isEmpty()) return;

    if (widgetConstructor) {
      final OutlineLocation location = computeLocation(outlineNode);

      int minChildIndent = Integer.MAX_VALUE;
      final ArrayList<OutlineLocation> childrenLocations = new ArrayList<>();
      int endLine = location.getLine();
      for (FlutterOutline child : children) {
        final OutlineLocation childLocation = computeLocation(child);
        if (childLocation.getLine() <= location.getLine()) {
          // Skip children that don't actually occur on a later line. There is no
          // way for us to draw good looking line art for them.
          // TODO(jacobr): consider adding these children anyway so we can render
          // them if there are edits and they are now properly formatted.
          continue;
        }

        minChildIndent = min(minChildIndent, childLocation.getIndent());
        endLine = max(endLine, childLocation.getLine());
        childrenLocations.add(childLocation);
      }
      if (!childrenLocations.isEmpty()) {
        // The indent is only used for sorting and disambiguating descriptors
        // as at render time we will pick the real indent for the outline based
        // on local edits that may have been made since the outline was computed.
        final int lineIndent = location.getIndent();
        final WidgetIndentGuideDescriptor descriptor = new WidgetIndentGuideDescriptor(
          parent,
          lineIndent,
          location.getLine(),
          endLine + 1,
          childrenLocations,
          location
        );
        if (!descriptor.childLines.isEmpty()) {
          widgetDescriptors.add(descriptor);
          parent = descriptor;
        }
      }
    }
    for (FlutterOutline child : children) {
      buildWidgetDescriptors(widgetDescriptors, child, parent);
    }
  }

  @NotNull
  private RangeHighlighter createHighlighter(MarkupModel mm, TextRangeDescriptorPair entry) {
    final TextRange range = entry.range;
    final FlutterSettings settings = FlutterSettings.getInstance();
    if (range.getEndOffset() >= myDocument.getTextLength() && DEBUG_WIDGET_INDENTS) {
      LOG.info("Warning: highlighter extends past the end of document.");
    }
    final RangeHighlighter highlighter =
      mm.addRangeHighlighter(
        Math.max(range.getStartOffset(), 0),
        Math.min(range.getEndOffset(), myDocument.getTextLength()),
        HighlighterLayer.FIRST,
        null,
        HighlighterTargetArea.EXACT_RANGE
      );
    if (entry.descriptor.parent == null && settings.isShowBuildMethodsOnScrollbar()) {
      highlighter.setErrorStripeMarkColor(BUILD_METHOD_STRIPE_COLOR);
      highlighter.setErrorStripeTooltip("Flutter build method");
      highlighter.setThinErrorStripeMark(true);
    }
    highlighter.setCustomRenderer(new WidgetCustomHighlighterRenderer(entry.descriptor, myDocument));
    return highlighter;
  }
}

/**
 * Data describing widget indents for an editor that is persisted across
 * multiple runs of the WidgetIndentsHighlightingPass.
 */
class WidgetIndentsPassData {
  /**
   * Descriptors describing the data model to render the widget indents.
   * <p>
   * This data is computed from the FlutterOutline and contains additional
   * information to manage how the locations need to be updated to reflect
   * edits to the documents.
   */
  List<WidgetIndentGuideDescriptor> myDescriptors = Collections.emptyList();

  /**
   * Descriptors combined with their current locations in the possibly modified document.
   */
  List<TextRangeDescriptorPair> myRangesWidgets = Collections.emptyList();

  /**
   * Highlighters that perform the actual rendering of the widget indent
   * guides.
   */
  List<RangeHighlighter> highlighters;

  /**
   * Source of truth for whether other UI overlaps with the widget indents.
   */
  WidgetIndentHitTester hitTester;

  /**
   * Outline the widget indents are based on.
   */
  FlutterOutline outline;
}

class TextRangeDescriptorPair {
  final TextRange range;
  final WidgetIndentGuideDescriptor descriptor;

  TextRangeDescriptorPair(TextRange range, WidgetIndentGuideDescriptor descriptor) {
    this.range = range;
    this.descriptor = descriptor;
  }
}

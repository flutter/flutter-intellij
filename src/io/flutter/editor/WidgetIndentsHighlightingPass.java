/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.psi.*;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.*;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterOutlineAttribute;
import org.jetbrains.annotations.NotNull;

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

  private static final Key<WidgetIndentsPassData> INDENTS_PASS_DATA_KEY = Key.create("INDENTS_PASS_DATA_KEY");

  /**
   * When this debugging flag is true, problematic text ranges are reported.
   */
  private final static boolean DEBUG_WIDGET_INDENTS = false;

  private final EditorEx myEditor;
  private final Document myDocument;
  private final Project myProject;
  private final VirtualFile myFile;
  private final boolean convertOffsets;
  private final PsiFile psiFile;
  private final EditorMouseEventService editorEventService;
  private final WidgetEditingContext context;

  WidgetIndentsHighlightingPass(
    @NotNull Project project,
    @NotNull EditorEx editor,
    boolean convertOffsets,
    FlutterDartAnalysisServer flutterDartAnalysisService,
    InspectorStateService inspectorStateService,
    EditorMouseEventService editorEventService,
    EditorPositionService editorPositionService
  ) {
    this.myDocument = editor.getDocument();
    this.myEditor = editor;
    this.myProject = project;
    this.myFile = editor.getVirtualFile();
    this.convertOffsets = convertOffsets;
    this.editorEventService = editorEventService;
    context = new WidgetEditingContext(
      myDocument, flutterDartAnalysisService, inspectorStateService, editorPositionService, myEditor);

    psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    final WidgetIndentsPassData data = getIndentsPassData();
    setIndentsPassData(editor, data);
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

  private static WidgetIndentsPassData getIndentsPassData(Editor editor) {
    if (editor == null) return null;
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

    List<RangeHighlighter> oldHighlighters = data.highlighters;
    if (oldHighlighters != null) {
      for (RangeHighlighter highlighter : oldHighlighters) {
        disposeHighlighter(highlighter);
      }
    }

    /*
     oldHighlighters = data.propertyHighlighters;
    if (oldHighlighters != null) {
      for (RangeHighlighter highlighter : oldHighlighters) {
        disposeHighlighter(highlighter);
      }
    }

     */
    setIndentsPassData(editor, null);
  }

  public static void run(@NotNull Project project,
                         @NotNull EditorEx editor,
                         @NotNull FlutterOutline outline,
                         FlutterDartAnalysisServer flutterDartAnalysisService,
                         InspectorStateService inspectorStateService,
                         EditorMouseEventService editorEventService,
                         EditorPositionService editorPositionService,
                         boolean convertOffsets
                         ) {
    final WidgetIndentsHighlightingPass widgetIndentsHighlightingPass = new WidgetIndentsHighlightingPass(
      project,
      editor,
      convertOffsets,
      flutterDartAnalysisService,
      inspectorStateService,
      editorEventService,
      editorPositionService
    );
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
    for (int i = 0; i< descriptors.size() - 1; i++) {
      descriptors.get(i).nextSibling = descriptors.get(i+1);
    }
    updateHitTester(new WidgetIndentHitTester(descriptors, myDocument), data);
    // TODO(jacobr): we need to trigger a rerender of highlighters that will render differently due to the changes in highlighters?
    data.myDescriptors = descriptors;
    doCollectInformationUpdateOutline(data);
    doApplyIndentInformationToEditor(data);
    // XXXdoApplyPropertyInformationToEditor(data);
    setIndentsPassData(data);
    updatePreviewHighlighter(myEditor.getMarkupModel(), data);
  }

  private void updateHitTester(WidgetIndentHitTester hitTester, WidgetIndentsPassData data) {
    if (Objects.equals(data.hitTester, hitTester)) {
      return;
    }
    FilteredIndentsHighlightingPass.onWidgetIndentsChanged(myEditor, data.hitTester, hitTester);
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
          range = descriptor.widget.getFullRange();
        }
        else {
          final int endOffset =
            descriptor.endLine < myDocument.getLineCount() ? myDocument.getLineStartOffset(descriptor.endLine) : myDocument.getTextLength();
          range = new TextRange(myDocument.getLineStartOffset(descriptor.startLine), endOffset);
        }
        descriptor.trackLocations(myDocument); // XXX we are tracking from multiple places.
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
      // This is similar to logic in FilteredIndentsHighlightingPass.java that also attempts to
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
          newHighlighters.add(createHighlighter(mm, entry, data));
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
        newHighlighters.add(createHighlighter(mm, ranges.get(i), data));
      }
    });

    data.highlighters = newHighlighters;
  }

  public void doApplyPropertyInformationToEditor(WidgetIndentsPassData data) {
    /*
    final MarkupModel mm = myEditor.getMarkupModel();

    final List<RangeHighlighter> oldHighlighters = data.propertyHighlighters;
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

     */
  }

  DartAnalysisServerService getAnalysisService() {
    // TODO(jacobr): cache this?
    return DartAnalysisServerService.getInstance(myProject);
  }

  /**
   * All calls to convert offsets for indent highlighting must go through this method.
   * <p>
   * Sometimes we need to use the raw offsets and sometimes we need
   * to use the converted offsets depending on whether the FlutterOutline
   * matches the current document or the expectations given by the
   *
   * @param node the FlutterOutline to retreive the offset for
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
    final CharSequence chars = myDocument.getCharsSequence();
    int indent;

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

  DartCallExpression getCallExpression(PsiElement element) {
    if (element == null) { return null; }
    if (element instanceof DartCallExpression) {
      return (DartCallExpression) element;
    }

    return getCallExpression(element.getParent());
  }
  private void buildWidgetDescriptors(
    final List<WidgetIndentGuideDescriptor> widgetDescriptors,
    FlutterOutline outlineNode,
    WidgetIndentGuideDescriptor parent
  ) {
    if (outlineNode == null) return;

    final String kind = outlineNode.getKind();
    final boolean widgetConstructor = "NEW_INSTANCE".equals(kind) || (parent != null && ("VARIABLE".equals(kind)));

    final List<FlutterOutline> children = outlineNode.getChildren();
//    if (children == null || children.isEmpty()) return;

    if (widgetConstructor) {
      final OutlineLocation location = computeLocation(outlineNode);
      int minChildIndent = Integer.MAX_VALUE;
      final ArrayList<OutlineLocation> childrenLocations = new ArrayList<>();
      int endLine = location.getLine();

      if (children != null) {
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
      }
      final Set<Integer> childrenOffsets = new HashSet<Integer>();
      for (OutlineLocation childLocation : childrenLocations) {
        childrenOffsets.add(childLocation.getGuideOffset());
      }

      final PsiElement element=  psiFile.findElementAt(location.getGuideOffset());
      final ArrayList<WidgetIndentGuideDescriptor.WidgetPropertyDescriptor> trustedAttributes = new ArrayList<>();
      final List<FlutterOutlineAttribute> attributes = outlineNode.getAttributes();
      if (attributes != null) {
        for (FlutterOutlineAttribute attribute : attributes) {
          trustedAttributes.add(new WidgetIndentGuideDescriptor.WidgetPropertyDescriptor(attribute));
        }
      }

      // XXX if (!childrenLocations.isEmpty())
      {
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
          location,
          trustedAttributes,
          outlineNode
        );
        // if (!descriptor.childLines.isEmpty())
        {
          widgetDescriptors.add(descriptor);
          parent = descriptor;
        }
      }
    }
    if (children != null) {
      for (FlutterOutline child : children) {
        buildWidgetDescriptors(widgetDescriptors, child, parent);
      }
    }
  }

  @NotNull
  private RangeHighlighter createHighlighter(MarkupModel mm, TextRangeDescriptorPair entry, WidgetIndentsPassData data) {
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
      highlighter.setErrorStripeMarkColor(FlutterEditorColors.BUILD_METHOD_STRIPE_COLOR);
      highlighter.setErrorStripeTooltip("Flutter build method");
      highlighter.setThinErrorStripeMark(true);
    }
    final WidgetViewModelData d = new WidgetViewModelData(entry.descriptor, highlighter, context);
    highlighter.setCustomRenderer(new WidgetCustomHighlighterRenderer(d, myProject));
    return highlighter;
  }

  @NotNull
  private void updatePreviewHighlighter(MarkupModel mm, WidgetIndentsPassData data) {
    if (data.previewsForEditor == null) {
      final TextRange range = new TextRange(0, Integer.MAX_VALUE);
      final RangeHighlighter highlighter =
        mm.addRangeHighlighter(
          0,
          myDocument.getTextLength(),
          HighlighterLayer.FIRST,
          null,
          HighlighterTargetArea.LINES_IN_RANGE
        );
      data.previewsForEditor = new PreviewsForEditor(context, editorEventService);
      highlighter.setCustomRenderer(data.previewsForEditor);
    }
    data.previewsForEditor.outlinesChanged(data.myDescriptors);
  }
  /*
  @NotNull
  private RangeHighlighter createPropertyHighlighter(MarkupModel mm, TextRangeDescriptorPair entry) {
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
    highlighter.setCustomRenderer(new PropertyValueRenderer(entry.descriptor, myDocument));
    return highlighter;
  }

   */
}

class TextRangeDescriptorPair {
  @NotNull final TextRange range;
  @NotNull final WidgetIndentGuideDescriptor descriptor;

  TextRangeDescriptorPair(@NotNull TextRange range, @NotNull WidgetIndentGuideDescriptor descriptor) {
    this.range = range;
    this.descriptor = descriptor;
  }
}
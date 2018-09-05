/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class EditorPerfDecorations implements Disposable {
  private static final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

  private static final int HIGHLIGHTER_LAYER = HighlighterLayer.SELECTION - 1;

  @NotNull
  private final FileEditor fileEditor;

  private boolean hasDecorations = false;

  EditorPerfDecorations(@NotNull FileEditor fileEditor) {
    this.fileEditor = fileEditor;

    addBlankMarker();
  }

  private void addBlankMarker() {
    ApplicationManager.getApplication().invokeLater(() -> {
      final Editor editor = ((TextEditor)fileEditor).getEditor();
      final MarkupModel markupModel = editor.getMarkupModel();

      if (hasDecorations) {
        removeHighlightersFromEditor(markupModel);
      }

      if (editor.getDocument().getTextLength() > 0) {
        final RangeHighlighter rangeHighlighter =
          markupModel.addLineHighlighter(0, HIGHLIGHTER_LAYER, new TextAttributes());
        rangeHighlighter.setLineMarkerRenderer(new BlankLineMarkerRenderer());

        hasDecorations = true;
      }
    });
  }

  public void updateFromSourceReport(
    ScriptManager scriptManager,
    @NotNull VirtualFile reloadFile,
    @NotNull SourceReport report
  ) {
    scriptManager.reset();

    final java.util.List<ScriptRef> scripts = new ArrayList<>();
    for (ScriptRef scriptRef : report.getScripts()) {
      scripts.add(scriptRef);
    }

    final FilePerfInfo perfInfo = new FilePerfInfo(reloadFile);

    for (SourceReportRange reportRange : report.getRanges()) {
      final SourceReportCoverage coverage = reportRange.getCoverage();
      if (coverage == null) {
        continue;
      }

      if (coverage.getHits().isEmpty()) {
        continue;
      }

      final ScriptRef scriptRef = scripts.get(reportRange.getScriptIndex());
      final String uri = scriptRef.getUri();
      if (uri.startsWith("file:")) {
        final VirtualFile file = virtualFileManager.findFileByUrl(uri);
        if (file != null && file.equals(reloadFile)) {
          scriptManager.populateFor(scriptRef);

          final Script script = scriptManager.getScriptFor(scriptRef);
          if (script == null) {
            continue;
          }

          for (List<Integer> encoded : script.getTokenPosTable()) {
            perfInfo.addUncovered(encoded.get(0) - 1);
          }

          for (int tokenPos : coverage.getHits()) {
            perfInfo.addCovered(scriptManager.getLineColumnPosForTokenPos(scriptRef, tokenPos));
          }

          for (int tokenPos : coverage.getMisses()) {
            perfInfo.addUncovered(scriptManager.getLineColumnPosForTokenPos(scriptRef, tokenPos));
          }
        }
      }
    }

    // Calculate coverage info for file, display in the UI.
    ApplicationManager.getApplication().invokeLater(() -> {
      final LineMarkerRenderer uncoveredRenderer = new UncoveredLineMarkerRenderer();
      final TextAttributes coveredAttributes = new TextAttributes();
      final TextAttributes uncoveredAttributes = new TextAttributes();

      assert fileEditor instanceof TextEditor;
      final Editor editor = ((TextEditor)fileEditor).getEditor();
      final MarkupModel markupModel = editor.getMarkupModel();

      removeHighlightersFromEditor(markupModel);

      int markerCount = 0;

      for (int line : perfInfo.getCoveredLines()) {
        final RangeHighlighter rangeHighlighter = markupModel.addLineHighlighter(line, HIGHLIGHTER_LAYER, coveredAttributes);

        final CoveredLineMarkerRenderer renderer =
          new CoveredLineMarkerRenderer(!perfInfo.isCovered(line - 1), !perfInfo.isCovered(line + 1));
        rangeHighlighter.setErrorStripeMarkColor(CoveredLineMarkerRenderer.coveredColor);
        rangeHighlighter.setThinErrorStripeMark(true);
        rangeHighlighter.setLineMarkerRenderer(renderer);

        markerCount++;
      }

      for (int line : perfInfo.getUncoveredLines()) {
        final RangeHighlighter rangeHighlighter =
          markupModel.addLineHighlighter(line, HIGHLIGHTER_LAYER, uncoveredAttributes);
        rangeHighlighter.setLineMarkerRenderer(uncoveredRenderer);

        markerCount++;
      }

      if (markerCount == 0) {
        final RangeHighlighter rangeHighlighter =
          markupModel.addLineHighlighter(0, HIGHLIGHTER_LAYER, new TextAttributes());
        rangeHighlighter.setLineMarkerRenderer(new BlankLineMarkerRenderer());
      }

      hasDecorations = true;
    });
  }

  private void removeHighlightersFromEditor(MarkupModel markupModel) {
    final List<RangeHighlighter> highlighters = new ArrayList<>();

    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      if (highlighter.getLineMarkerRenderer() instanceof FlutterLineMarkerRenderer) {
        highlighters.add(highlighter);
      }
    }

    for (RangeHighlighter highlighter : highlighters) {
      markupModel.removeHighlighter(highlighter);
    }

    hasDecorations = false;
  }

  public void flushDecorations() {
    if (hasDecorations && fileEditor.isValid()) {
      hasDecorations = false;

      ApplicationManager.getApplication().invokeLater(() -> {
        final MarkupModel markupModel = ((TextEditor)fileEditor).getEditor().getMarkupModel();
        removeHighlightersFromEditor(markupModel);
      });
    }
  }

  @Override
  public void dispose() {
    flushDecorations();
  }
}

abstract class FlutterLineMarkerRenderer implements LineMarkerRenderer, LineMarkerRendererEx {
  static final int curvature = 2;

  @NotNull
  @Override
  public LineMarkerRendererEx.Position getPosition() {
    return LineMarkerRendererEx.Position.LEFT;
  }
}

class CoveredLineMarkerRenderer extends FlutterLineMarkerRenderer {
  static final Color coveredColor = new JBColor(new Color(0x0091ea), new Color(0x0091ea));

  private final boolean isStart;
  private final boolean isEnd;

  CoveredLineMarkerRenderer(boolean isStart, boolean isEnd) {
    this.isStart = isStart;
    this.isEnd = isEnd;
  }

  static int hue = 0;
  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    final int height = r.height;

    GraphicsUtil.setupAAPainting(g);
    //g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    final Font font = UIUtil.getFont(UIUtil.FontSize.MINI, UIUtil.getButtonFont());
    g.setFont(font);

    String text = Integer.toString(new Random().nextInt(100));

    final Rectangle2D bounds = g.getFontMetrics().getStringBounds(text, g);

    final int width = Math.max(r.width, (int)bounds.getWidth() + 4);

    hue += 10;
    hue = hue % 360;
    Color backgroundColor = Color.getHSBColor((float)hue / 360.0f, 1.0f, 1.0f);
    g.setColor(backgroundColor);

    if (!isStart && !isEnd) {
      g.fillRect(r.x + 2, r.y, width, height);
    }
    else {
      g.fillRoundRect(r.x + 2, r.y, width, height, curvature, curvature);

      final int diff = height / 2;

      if (!isStart) {
        g.fillRect(r.x + 2, r.y, width, diff);
      }

      if (!isEnd) {
        g.fillRect(r.x + 2, r.y + diff, width, height - diff);
      }
    }
    g.setColor(JBColor.white);
    g.drawString(text, r.x + 4, r.y + r.height - 4);
  }
}

class UncoveredLineMarkerRenderer extends FlutterLineMarkerRenderer {
  private static final Color uncoveredColor = JBColor.LIGHT_GRAY;

  UncoveredLineMarkerRenderer() {
  }

  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    final int width = r.width - 4;
    final int height = r.height / 2;

    g.setColor(uncoveredColor);
    g.fillRoundRect(r.x + 2, r.y + (r.height - height) / 2, width, height, curvature, curvature);
  }
}

class BlankLineMarkerRenderer extends FlutterLineMarkerRenderer {
  BlankLineMarkerRenderer() {
  }

  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
  }
}

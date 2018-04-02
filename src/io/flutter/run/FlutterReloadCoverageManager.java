/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ui.JBColor;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.consumer.ServiceExtensionConsumer;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// TODO: get coverage data from the VM

// TODO: which files are we tracking

// TODO: display coverage in editors

// TODO: reset after a reload / restart, and poll for a while after that

// see DartMethodLineMarkerProvider and
// <codeInsight.lineMarkerProvider language="Dart" implementationClass="com.jetbrains.lang.dart.ide.marker.DartMethodLineMarkerProvider"/>

// TODO: how do we tell IntelliJ to re-draw the markers?

// TextAnnotationGutterProvider, EditorGutter, EditorGutterComponentEx
// GutterIconRenderer, GutterMark, LineMarkerRenderer
// RangeHighlighter#setLineMarkerRenderer(LineMarkerRenderer)
// MarkupModel#addRangeHighlighter(int, int, int, TextAttributes, HighlighterTargetArea)
// DiffMarkup
// CoverageLineMarkerRenderer
// LineMarkerRenderer
// EditorColors.ADDED_LINES_COLOR
// LineStatusMarkerRenderer
// highligher, with a linemarkerrenderer

public class FlutterReloadCoverageManager {
  private @NotNull final FlutterApp app;
  private FlutterApp.State lastState;
  private final List<RangeHighlighter> rangeHighlighters = new ArrayList<>();

  private IsolateRef currentIsolate;

  public FlutterReloadCoverageManager(@NotNull FlutterApp app) {
    this.app = app;

    app.addStateListener(new FlutterApp.FlutterAppListener() {
      @Override
      public void stateChanged(final FlutterApp.State newState) {
        // on a RELOADING ==> STARTED transition, start polling
        // on a TERMINATING or TERMINATED event, tear everything down
        // on any other event, stop polling and remove any editor contributions

        if (lastState == FlutterApp.State.RELOADING && newState == FlutterApp.State.STARTED) {
          handlePostReload();
        }
        else if (newState == FlutterApp.State.TERMINATING || newState == FlutterApp.State.TERMINATED) {
          handleTerminating();
        }
        else {
          stopPollingRemoveCoverage();
        }

        lastState = newState;
      }

      @Override
      public void notifyAppRestarted() {
        stopPollingRemoveCoverage();
      }
    });

    assert app.getVmService() != null;
    app.getVmService().addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        if (event.getKind() == EventKind.IsolateExit) {
          currentIsolate = null;
        }

        if (currentIsolate == null && event.getKind() == EventKind.Extension && event.getExtensionKind().startsWith("Flutter")) {
          // Flutter.FrameworkInitialization, Flutter.FirstFrame, Flutter.Frame
          currentIsolate = event.getIsolate();
        }
      }
    });
  }

  private void handlePostReload() {
    // TODO:
    ApplicationManager.getApplication().invokeLater(() -> {
      System.out.println("handlePostReload");

      final Editor editor = FileEditorManager.getInstance(app.getProject()).getSelectedTextEditor();

      if (editor != null) {
        final MarkupModel markupModel = editor.getMarkupModel();

        // EditorColors.ADDED_LINES_COLOR
        // CodeInsightColors.LINE_FULL_COVERAGE
        final EditorColorsScheme scheme = getColorScheme(editor);
        //scheme.getColor(EditorColors.ADDED_LINES_COLOR).darker();
        //scheme.getColor(EditorColors.DELETED_LINES_COLOR).brighter();
        final Color coveredColor = new JBColor(new Color(0x0091ea), new Color(0x0091ea));
        final Color uncoveredColor = JBColor.GRAY;

        // INFORMATION_ATTRIBUTES
        // TODO: for now, don't highlight text in-line.
        final TextAttributes coveredAttributes = new TextAttributes();
        final TextAttributes uncoveredAttributes = new TextAttributes();
        //uncoveredAttributes.setEffectType(EffectType.BOLD_LINE_UNDERSCORE);
        //uncoveredAttributes.setEffectColor(uncoveredColor);
        //attributes.setErrorStripeColor(Color.BLUE);

        // TODO:
        //highlighter.setErrorStripeMarkColor(markerRenderer.getErrorStripeColor(editor));
        //highlighter.setThinErrorStripeMark(true);

        RangeHighlighter rangeHighlighter = markupModel.addLineHighlighter(79, HighlighterLayer.SELECTION - 1, uncoveredAttributes);
        //rangeHighlighter.setThinErrorStripeMark(true);
        rangeHighlighter.setErrorStripeMarkColor(uncoveredColor);
        rangeHighlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(uncoveredColor));
        rangeHighlighters.add(rangeHighlighter);

        rangeHighlighter = markupModel.addLineHighlighter(90, HighlighterLayer.SELECTION - 1, coveredAttributes);
        rangeHighlighter.setThinErrorStripeMark(true);
        rangeHighlighter.setErrorStripeMarkColor(coveredColor);
        rangeHighlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(coveredColor));
        rangeHighlighters.add(rangeHighlighter);

        rangeHighlighter = markupModel.addLineHighlighter(91, HighlighterLayer.SELECTION - 1, coveredAttributes);
        rangeHighlighter.setThinErrorStripeMark(true);
        rangeHighlighter.setErrorStripeMarkColor(coveredColor);
        rangeHighlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(coveredColor));
        rangeHighlighters.add(rangeHighlighter);
      }
    });

    // TODO: parse coverage information
    // TODO: what's the current isolate? which one just reloaded (or, is running the app)?
    if (currentIsolate != null) {
      assert app.getVmService() != null;

      // TODO: fix the library so we can call getSourceReport()
      final JsonObject params = new JsonObject();
      final JsonArray arr = new JsonArray();
      arr.add("Coverage");
      params.add("reports", arr);

      app.getVmService().callServiceExtension(currentIsolate.getId(), "getSourceReport", params, new ServiceExtensionConsumer() {
        @Override
        public void received(JsonObject object) {
          final SourceReport report = new SourceReport(object);

          System.out.println(report);
        }

        @Override
        public void onError(RPCError error) {
          // TODO:

        }
      });
    }
  }

  private static EditorColorsScheme getColorScheme(@Nullable Editor editor) {
    return editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }

  private void stopPollingRemoveCoverage() {
    if (!rangeHighlighters.isEmpty()) {

      ApplicationManager.getApplication().invokeLater(() -> {
        final Editor editor = FileEditorManager.getInstance(app.getProject()).getSelectedTextEditor();

        if (editor != null) {
          final MarkupModel markupModel = editor.getMarkupModel();
          for (RangeHighlighter highlighter : rangeHighlighters) {
            markupModel.removeHighlighter(highlighter);
          }
          rangeHighlighters.clear();
        }
      });
    }

    // TODO:

    System.out.println("stopPollingRemoveCoverage");
  }

  private void handleTerminating() {
    // TODO:
    stopPollingRemoveCoverage();

    System.out.println("handleTerminating");
  }
}

class MyLineMarkerRenderer implements LineMarkerRenderer {
  //private static final int DEEPNESS = 0;
  //private static final int THICKNESS = 1;
  private final Color myColor;

  MyLineMarkerRenderer(@NotNull Color color) {
    this.myColor = color;
  }

  public void paint(Editor editor, Graphics g, Rectangle r) {
    final int height = r.height; // + editor.getLineHeight();
    //g.setColor(ColorUtil.toAlpha(myColor, 150));
    g.setColor(myColor);
    g.fillRect(r.x, r.y, r.width, height);
    //g.fillRect(r.x + 1, r.y, 0, 1);
    //g.fillRect(r.x + 1, r.y + height - 1, 0, 1);
  }
}

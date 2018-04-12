/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.Semaphore;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.GetLibraryConsumer;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.consumer.ServiceExtensionConsumer;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FlutterReloadCoverageManager {
  private static final long RESPONSE_WAIT_TIMEOUT = 3000;

  private @NotNull final FlutterApp app;
  private FlutterApp.State lastState;

  private IsolateRef currentIsolate;
  private ScriptManager scriptManager;

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
          // TODO:
          //stopPollingRemoveCoverage();
        }

        lastState = newState;
      }

      @Override
      public void notifyAppRestarted() {
        stopPollingRemoveCoverage();
      }
    });

    assert app.getVmService() != null;

    scriptManager = new ScriptManager(app.getVmService());

    app.getVmService().addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        if (event.getKind() == EventKind.IsolateExit) {
          currentIsolate = null;
        }

        if (currentIsolate == null && event.getKind() == EventKind.Extension && event.getExtensionKind().startsWith("Flutter")) {
          // Flutter.FrameworkInitialization, Flutter.FirstFrame, Flutter.Frame
          currentIsolate = event.getIsolate();

          scriptManager.setCurrentIsolate(currentIsolate);
        }
      }
    });
  }

  private void handlePostReload() {
    // TODO:
    //ApplicationManager.getApplication().invokeLater(() -> {
    //  System.out.println("handlePostReload");
    //
    //  final Editor editor = FileEditorManager.getInstance(app.getProject()).getSelectedTextEditor();
    //
    //  if (editor != null) {
    //    final MarkupModel markupModel = editor.getMarkupModel();
    //
    //    // EditorColors.ADDED_LINES_COLOR
    //    // CodeInsightColors.LINE_FULL_COVERAGE
    //    final EditorColorsScheme scheme = getColorScheme(editor);
    //    //scheme.getColor(EditorColors.ADDED_LINES_COLOR).darker();
    //    //scheme.getColor(EditorColors.DELETED_LINES_COLOR).brighter();
    //    final Color coveredColor = new JBColor(new Color(0x0091ea), new Color(0x0091ea));
    //    final Color uncoveredColor = JBColor.GRAY;
    //
    //    // INFORMATION_ATTRIBUTES
    //    // TODO: for now, don't highlight text in-line.
    //    final TextAttributes coveredAttributes = new TextAttributes();
    //    final TextAttributes uncoveredAttributes = new TextAttributes();
    //    //uncoveredAttributes.setEffectType(EffectType.BOLD_LINE_UNDERSCORE);
    //    //uncoveredAttributes.setEffectColor(uncoveredColor);
    //    //attributes.setErrorStripeColor(Color.BLUE);
    //
    //    // TODO:
    //    //highlighter.setErrorStripeMarkColor(markerRenderer.getErrorStripeColor(editor));
    //    //highlighter.setThinErrorStripeMark(true);
    //
    //    RangeHighlighter rangeHighlighter = markupModel.addLineHighlighter(79, HighlighterLayer.SELECTION - 1, uncoveredAttributes);
    //    //rangeHighlighter.setThinErrorStripeMark(true);
    //    rangeHighlighter.setErrorStripeMarkColor(uncoveredColor);
    //    rangeHighlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(uncoveredColor));
    //    rangeHighlighters.add(rangeHighlighter);
    //
    //    // addRangeHighlighter
    //    rangeHighlighter = markupModel.addLineHighlighter(90, HighlighterLayer.SELECTION - 1, coveredAttributes);
    //    rangeHighlighter.setThinErrorStripeMark(true);
    //    rangeHighlighter.setErrorStripeMarkColor(coveredColor);
    //    rangeHighlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(coveredColor));
    //    rangeHighlighters.add(rangeHighlighter);
    //
    //    rangeHighlighter = markupModel.addLineHighlighter(91, HighlighterLayer.SELECTION - 1, coveredAttributes);
    //    rangeHighlighter.setThinErrorStripeMark(true);
    //    rangeHighlighter.setErrorStripeMarkColor(coveredColor);
    //    rangeHighlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(coveredColor));
    //    rangeHighlighters.add(rangeHighlighter);
    //  }
    //});

    final VirtualFile reloadFile = app.getLastReloadFile();

    if (currentIsolate != null && reloadFile != null) {
      assert app.getVmService() != null;

      @Nullable final ScriptRef scriptRef = getScriptRefFor(reloadFile);
      if (scriptRef != null) {
        final JsonObject params = new JsonObject();
        final JsonArray arr = new JsonArray();
        arr.add("Coverage");

        params.add("reports", arr);

        // Pass in just the current scriptId.
        params.addProperty("scriptId", scriptRef.getId());

        // Make sure we get good 'not covered' info.
        // TODO: Does this work under the CFE?
        params.addProperty("forceCompile", true);

        // TODO(devoncarew): Fix the vm service Java library so we can call getSourceReport().
        app.getVmService().callServiceExtension(currentIsolate.getId(), "getSourceReport", params, new ServiceExtensionConsumer() {
          @Override
          public void received(JsonObject object) {
            JobScheduler.getScheduler().schedule(() -> {
              final SourceReport report = new SourceReport(object);
              updateFromSourceReport(reloadFile, report);
            }, 0, TimeUnit.MILLISECONDS);
          }

          @Override
          public void onError(RPCError error) {
            // TODO:
          }
        });
      }
    }
  }

  @Nullable
  private ScriptRef getScriptRefFor(@NotNull VirtualFile file) {
    final Isolate isolate = getCurrentIsolate();
    if (isolate == null) {
      return null;
    }

    // TODO: in non-CFE, these libraries can be pre-fixed with the temp path (the uri prefix from flutter_tools)

    for (LibraryRef libraryRef : isolate.getLibraries()) {
      final String uri = libraryRef.getUri();

      if (uri.startsWith("file:")) {
        final VirtualFile libraryFile = virtualFileManager.findFileByUrl(uri);

        if (file.equals(libraryFile)) {
          System.out.println(libraryRef.getUri());

          final Library library = getLibrary(libraryRef);
          if (library != null) {
            for (ScriptRef scriptRef : library.getScripts()) {
              System.out.println(scriptRef.getUri());
            }

            if (!library.getScripts().isEmpty()) {
              return library.getScripts().get(0);
            }
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private Isolate getCurrentIsolate() {
    assert app.getVmService() != null;

    final Ref<Isolate> resultRef = Ref.create();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    app.getVmService().getIsolate(currentIsolate.getId(), new GetIsolateConsumer() {
      @Override
      public void received(Isolate isolate) {
        resultRef.set(isolate);
        semaphore.up();
      }

      @Override
      public void received(Sentinel sentinel) {
        semaphore.up();
      }

      @Override
      public void onError(RPCError error) {
        semaphore.up();
      }
    });
    semaphore.waitFor(RESPONSE_WAIT_TIMEOUT);
    return resultRef.get();
  }

  @Nullable
  private Library getLibrary(LibraryRef libraryRef) {
    assert app.getVmService() != null;

    final Ref<Library> resultRef = Ref.create();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    app.getVmService().getLibrary(currentIsolate.getId(), libraryRef.getId(), new GetLibraryConsumer() {
      @Override
      public void received(Library library) {
        resultRef.set(library);
        semaphore.up();
      }

      @Override
      public void onError(RPCError error) {
        semaphore.up();
      }
    });
    semaphore.waitFor(RESPONSE_WAIT_TIMEOUT);
    return resultRef.get();
  }

  final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

  private void updateFromSourceReport(@NotNull VirtualFile reloadFile, @NotNull SourceReport report) {
    scriptManager.reset();

    final List<ScriptRef> scripts = new ArrayList<>();
    for (ScriptRef scriptRef : report.getScripts()) {
      scripts.add(scriptRef);
    }

    final FileCoverageInfo coverageInfo = new FileCoverageInfo(reloadFile);

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
        //if (file != null && app.getModule() != null && ModuleUtilCore.moduleContainsFile(app.getModule(), file, false)) {
        if (file != null && file.equals(reloadFile)) {
          scriptManager.populateFor(scriptRef);

          //final int startIndex = reportRange.getStartPos();
          //final int endIndex = reportRange.getEndPos();

          final Script script = scriptManager.getScriptFor(scriptRef);
          for (List<Integer> encoded : script.getTokenPosTable()) {
            coverageInfo.addUncovered(encoded.get(0) - 1);
          }

          for (int tokenPos : coverage.getHits()) {
            coverageInfo.addCovered(scriptManager.getLineColumnPosForTokenPos(scriptRef, tokenPos));
          }

          for (int tokenPos : coverage.getMisses()) {
            coverageInfo.addUncovered(scriptManager.getLineColumnPosForTokenPos(scriptRef, tokenPos));
          }
        }
      }
    }

    // Calculate coverage info for file, display in the UI.
    ApplicationManager.getApplication().invokeLater(() -> {
      final FileEditor[] editors = FileEditorManager.getInstance(app.getProject()).getAllEditors(coverageInfo.getFile());

      //final Color coveredColor = new JBColor(new Color(0x0091ea), new Color(0x0091ea));
      //final Color uncoveredColor = JBColor.GRAY;
      final Color coveredColor = new JBColor(new Color(0x0091ea), new Color(0x0091ea));
      final LineMarkerRenderer coveredRenderer = new MyLineMarkerRenderer(coveredColor);

      final Color uncoveredColor = JBColor.LIGHT_GRAY;
      final LineMarkerRenderer uncoveredRenderer = new MyLineMarkerRenderer(uncoveredColor);

      final TextAttributes coveredAttributes = new TextAttributes();
      final TextAttributes uncoveredAttributes = new TextAttributes();
      //uncoveredAttributes.setBackgroundColor(Color.lightGray);

      for (FileEditor editor : editors) {
        if (!(editor instanceof TextEditor)) {
          continue;
        }

        removeHighlightersFromEditor(((TextEditor)editor).getEditor());

        final MarkupModel markupModel = ((TextEditor)editor).getEditor().getMarkupModel();

        // TODO: rounded corners
        // TODO: extend the coverage info to methods, if all the lines are reported as covered
        // TODO: if the file is part of the app (if it could contain coverage info),
        //       add at least one marker (even if a hidden one)
        // TODO: auto-update when we receive frames
        // TODO: auto-update when the active editor changes

        for (int line : coverageInfo.getCoveredLines()) {
          final RangeHighlighter rangeHighlighter = markupModel.addLineHighlighter(line, HighlighterLayer.SELECTION - 1, coveredAttributes);
          rangeHighlighter.setErrorStripeMarkColor(coveredColor);
          rangeHighlighter.setThinErrorStripeMark(true);
          rangeHighlighter.setLineMarkerRenderer(coveredRenderer);
        }

        //for (int line : coverageInfo.getUncoveredLines()) {
        //  final RangeHighlighter rangeHighlighter = markupModel.addLineHighlighter(line, HighlighterLayer.SELECTION - 1, uncoveredAttributes);
        //  //rangeHighlighter.setErrorStripeMarkColor(uncoveredColor);
        //  //rangeHighlighter.setThinErrorStripeMark(true);
        //  rangeHighlighter.setLineMarkerRenderer(uncoveredRenderer);
        //}
      }
    });
  }

  private static EditorColorsScheme getColorScheme(@Nullable Editor editor) {
    return editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }

  private void stopPollingRemoveCoverage() {
    // TODO: make this more efficient

    ApplicationManager.getApplication().invokeLater(() -> {
      // TODO: get the editors we previously displayed coverage info for
      final Editor editor = FileEditorManager.getInstance(app.getProject()).getSelectedTextEditor();
      removeHighlightersFromEditor(editor);
    });

    System.out.println("stopPollingRemoveCoverage");
  }

  private void removeHighlightersFromEditor(@Nullable Editor editor) {
    if (editor == null) {
      return;
    }

    final List<RangeHighlighter> highlighters = new ArrayList<>();

    final MarkupModel markupModel = editor.getMarkupModel();

    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      if (highlighter.getLineMarkerRenderer() instanceof MyLineMarkerRenderer) {
        highlighters.add(highlighter);
      }
    }

    for (RangeHighlighter highlighter : highlighters) {
      markupModel.removeHighlighter(highlighter);
    }
  }

  private void handleTerminating() {
    // TODO:
    stopPollingRemoveCoverage();

    System.out.println("handleTerminating");
  }
}

class MyLineMarkerRenderer implements LineMarkerRenderer, LineMarkerRendererEx {
  private final Color myColor;

  MyLineMarkerRenderer(@NotNull Color color) {
    this.myColor = color;
  }

  @NotNull
  @Override
  public LineMarkerRendererEx.Position getPosition() {
    return LineMarkerRendererEx.Position.LEFT;
  }

  public void paint(Editor editor, Graphics g, Rectangle r) {
    final int height = r.height;
    g.setColor(myColor);
    g.fillRect(r.x, r.y, r.width, height);
    //g.fillRect(r.x + 1, r.y, 0, 1);
    //g.fillRect(r.x + 1, r.y + height - 1, 0, 1);
  }
}

class ScriptManager {
  private static final long RESPONSE_WAIT_TIMEOUT = 3000;

  @NotNull
  private final VmService vmService;
  private IsolateRef isolateRef;
  private final Map<String, Script> scriptMap = new HashMap<>();
  private final Map<String, TIntObjectHashMap<Pair<Integer, Integer>>> linesAndColumnsMap = new THashMap<>();

  public ScriptManager(@NotNull VmService vmService) {
    this.vmService = vmService;
  }

  public void reset() {
    scriptMap.clear();
  }

  public void setCurrentIsolate(IsolateRef isolateRef) {
    this.isolateRef = isolateRef;
  }

  public void populateFor(ScriptRef scriptRef) {
    if (!scriptMap.containsKey(scriptRef.getId())) {
      scriptMap.put(scriptRef.getId(), getScriptSync(scriptRef));
      linesAndColumnsMap.put(scriptRef.getId(), createTokenPosToLineAndColumnMap(scriptMap.get(scriptRef.getId())));
    }
  }

  public Pair<Integer, Integer> getLineColumnPosForTokenPos(ScriptRef scriptRef, int tokenPos) {
    final TIntObjectHashMap<Pair<Integer, Integer>> map = linesAndColumnsMap.get(scriptRef.getId());
    return map == null ? null : map.get(tokenPos);
  }

  private Script getScriptSync(@NotNull final ScriptRef scriptRef) {
    final Ref<Script> resultRef = Ref.create();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    vmService.getObject(isolateRef.getId(), scriptRef.getId(), new GetObjectConsumer() {
      @Override
      public void received(Obj script) {
        resultRef.set((Script)script);
        semaphore.up();
      }

      @Override
      public void received(Sentinel response) {
        semaphore.up();
      }

      @Override
      public void onError(RPCError error) {
        semaphore.up();
      }
    });

    semaphore.waitFor(RESPONSE_WAIT_TIMEOUT);
    return resultRef.get();
  }

  private static TIntObjectHashMap<Pair<Integer, Integer>> createTokenPosToLineAndColumnMap(@Nullable final Script script) {
    if (script == null) {
      return null;
    }

    // Each subarray consists of a line number followed by (tokenPos, columnNumber) pairs;
    // see https://github.com/dart-lang/vm_service_drivers/blob/master/dart/tool/service.md#script.
    final TIntObjectHashMap<Pair<Integer, Integer>> result = new TIntObjectHashMap<>();

    for (List<Integer> lineAndPairs : script.getTokenPosTable()) {
      final Iterator<Integer> iterator = lineAndPairs.iterator();
      final int line = Math.max(0, iterator.next() - 1);
      while (iterator.hasNext()) {
        final int tokenPos = iterator.next();
        final int column = Math.max(0, iterator.next() - 1);
        result.put(tokenPos, Pair.create(line, column));
      }
    }

    return result;
  }

  public Script getScriptFor(ScriptRef ref) {
    return scriptMap.get(ref.getId());
  }
}

class FileCoverageInfo {
  private final VirtualFile file;

  private final TIntHashSet coveredLines = new TIntHashSet();
  private final TIntHashSet uncoveredLines = new TIntHashSet();

  public FileCoverageInfo(VirtualFile file) {
    this.file = file;
  }

  public VirtualFile getFile() {
    return file;
  }

  public void addCovered(Pair<Integer, Integer> pos) {
    if (pos == null) {
      return;
    }

    final int line = pos.first;
    coveredLines.add(line);
    uncoveredLines.remove(line);
  }

  public void addUncovered(int line) {
    if (!coveredLines.contains(line)) {
      uncoveredLines.add(line);
    }
  }

  public void addUncovered(Pair<Integer, Integer> pos) {
    if (pos == null) {
      return;
    }
    final int line = pos.first;
    if (!coveredLines.contains(line)) {
      uncoveredLines.add(line);
    }
  }

  public int[] getCoveredLines() {
    return coveredLines.toArray();
  }

  public int[] getUncoveredLines() {
    return uncoveredLines.toArray();
  }
}

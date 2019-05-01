/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.analyzer.DartClosingLabelManager;
import io.flutter.FlutterUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.List;

/**
 * Factory that drives all rendering of widget indents.
 */
public class WidgetIndentsHighlightingPassFactory implements TextEditorHighlightingPassFactory, Disposable {
  // This is a debugging flag to track down bugs that are hard to spot if
  // analysis server updates are occuring at their normal rate. If there are
  // bugs, With this flag on you should be able to spot flickering or invalid
  // widget indent guide lines after editing code containing guides.
  private static final boolean SIMULATE_SLOW_ANALYSIS_UPDATES = false;

  private final Project project;
  private final FlutterDartAnalysisServer flutterDartAnalysisService;
  /**
   * Outlines for the currently visible files.
   */
  private final Map<String, FlutterOutline> currentOutlines;
  /**
   * Outline listeners for the currently visible files.
   */
  private final Map<String, FlutterOutlineListener> outlineListeners = new HashMap<>();

  // Current configuration settings used to display Widget Indent Guides cached
  // from the FlutterSettings class.
  private boolean isShowMultipleChildrenGuides;
  private boolean isShowBuildMethodGuides;
  private boolean isDisableDartClosingLabels;

  private final FlutterSettings.Listener settingsListener = () -> {
    final FlutterSettings settings = FlutterSettings.getInstance();
    // Skip if none of the settings that impact Widget Idents were changed.
    if (isShowBuildMethodGuides == settings.isShowBuildMethodGuides() &&
        isShowMultipleChildrenGuides == settings.isShowMultipleChildrenGuides() &&
        isDisableDartClosingLabels == settings.isDisableDartClosingLabels()) {
      // Change doesn't matter for us.
      return;
    }
    syncSettings(settings);

    for (EditorEx editor : getActiveDartEditors()) {
      updateEditorSettings(editor);
      // To be safe, avoid rendering artfacts when settings were changed
      // that only impacted rendering.
      editor.repaint(0, editor.getDocument().getTextLength());
    }
  };

  public WidgetIndentsHighlightingPassFactory(Project project) {
    this.project = project;
    TextEditorHighlightingPassRegistrar.getInstance(project)
      .registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false);
    currentOutlines = new HashMap<>();
    flutterDartAnalysisService = FlutterDartAnalysisServer.getInstance(project);

    syncSettings(FlutterSettings.getInstance());
    FlutterSettings.getInstance().addListener(settingsListener);

    final EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        final Editor editor = event.getEditor();
        if (editor.getProject() != project) return;
        if (!(editor instanceof EditorEx)) return;
        final EditorEx editorEx = (EditorEx)editor;
        WidgetIndentsHighlightingPass.onCaretPositionChanged(editorEx, event.getCaret());
      }
    }, this);

    updateActiveEditors();
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        updateActiveEditors();
      }
    });
  }

  List<EditorEx> getActiveDartEditors() {
    final FileEditor[] editors = FileEditorManager.getInstance(project).getSelectedEditors();
    final List<EditorEx> dartEditors = new ArrayList<>();
    for (FileEditor fileEditor : editors) {
      if (!(fileEditor instanceof TextEditor)) continue;
      final TextEditor textEditor = (TextEditor)fileEditor;
      final Editor editor = textEditor.getEditor();
      if (editor instanceof EditorEx) {
        dartEditors.add((EditorEx)editor);
      }
    }
    return dartEditors;
  }

  private void clearListeners() {
    for (Map.Entry<String, FlutterOutlineListener> entry : outlineListeners.entrySet()) {
      final String path = entry.getKey();
      final FlutterOutlineListener listener = outlineListeners.remove(path);
      if (listener != null) {
        flutterDartAnalysisService.removeOutlineListener(path, listener);
      }
    }
    outlineListeners.clear();
    currentOutlines.clear();
  }

  private void updateActiveEditors() {
    if (!FlutterSettings.getInstance().isShowBuildMethodGuides()) {
      clearListeners();
      return;
    }

    final VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    final VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();

    final Set<String> newPaths = new HashSet<>();
    for (VirtualFile file : files) {
      if (FlutterUtils.couldContainWidgets(file)) {
        newPaths.add(file.getPath());
      }
    }

    // Remove obsolete outline listeners.
    synchronized (outlineListeners) {
      final List<String> obsoletePaths = new ArrayList<>();
      for (final String path : outlineListeners.keySet()) {
        if (!newPaths.contains(path)) {
          obsoletePaths.add(path);
        }
      }
      for (final String path : obsoletePaths) {
        final FlutterOutlineListener listener = outlineListeners.remove(path);
        if (listener != null) {
          flutterDartAnalysisService.removeOutlineListener(path, listener);
        }
        // Clear the current outline as it may become out of date before the
        // file is visible again.
        currentOutlines.remove(path);
      }

      // Register new outline listeners.
      for (final String path : newPaths) {
        if (outlineListeners.containsKey(path)) continue;
        final FlutterOutlineListener listener =
          (filePath, outline, instrumentedCode) -> {
            synchronized (outlineListeners) {
              if (!outlineListeners.containsKey(path)) {
                // The outline listener subscription was already cancelled.
                return;
              }
            }
            synchronized (currentOutlines) {
              currentOutlines.put(path, outline);
            }
            ApplicationManager.getApplication().invokeLater(() -> {
              // Find visible editors for the path. If the file is not actually
              // being displayed on screen, there is no need to go through the
              // work of updating the outline.
              for (EditorEx editor : getActiveDartEditors()) {
                if (!editor.isDisposed() && Objects.equals(editor.getVirtualFile().getCanonicalPath(), path)) {
                  runWidgetIndentsPass(editor, outline);
                }
              }
            });
          };
        outlineListeners.put(path, listener);
        flutterDartAnalysisService.addOutlineListener(FileUtil.toSystemDependentName(path), listener);
      }
    }
  }

  private void syncSettings(FlutterSettings settings) {
    if (isShowBuildMethodGuides != settings.isShowBuildMethodGuides()) {
      isShowBuildMethodGuides = settings.isShowBuildMethodGuides();
      updateActiveEditors();
      if (settings.isDisableDartClosingLabels()) {
        DartClosingLabelManager.getInstance().setShowClosingLabels(!isShowBuildMethodGuides);
      }
    }
    if (settings.isShowBuildMethodGuides() && isDisableDartClosingLabels != settings.isDisableDartClosingLabels()) {
      isDisableDartClosingLabels = settings.isDisableDartClosingLabels();
      DartClosingLabelManager.getInstance().setShowClosingLabels(!isDisableDartClosingLabels);

    }
    isShowMultipleChildrenGuides = settings.isShowMultipleChildrenGuides() && isShowBuildMethodGuides;
  }

  private void updateEditorSettings(EditorEx editor) {
    // We have to suppress the system indent guides when displaying
    // WidgetIndentGuides as the system indent guides will overlap causing
    // artifacts. See the io.flutter.editor.IdentsGuides class that we use
    // instead which supports filtering out regular indent guides that overlap
    // with indent guides.
    editor.getSettings().setIndentGuidesShown(!isShowBuildMethodGuides);
  }

  @Override
  @NotNull
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor e) {
    if (!FlutterSettings.getInstance().isShowBuildMethodGuides()) {
      if (e instanceof EditorEx) {
        // Cleanup highlighters from the widget indents pass if it was
        // previously enabled.
        ApplicationManager.getApplication().invokeLater(() -> {
          WidgetIndentsHighlightingPass.cleanupHighlighters((EditorEx)e);
        });
      }

      // Return a placeholder editor highlighting pass. The user will get the
      // regular IntelliJ platform provided FliteredIndentsHighlightingPass in this case.
      // This is the special case where the user disabled the
      // WidgetIndentsGuides after previously having them setup.
      return new PlaceholderHighlightingPass(
        project,
        e.getDocument(),
        false
      );
    }
    final FliteredIndentsHighlightingPass fliteredIndentsHighlightingPass = new FliteredIndentsHighlightingPass(project, e, file);
    if (!(e instanceof EditorEx)) return fliteredIndentsHighlightingPass;
    final EditorEx editor = (EditorEx)e;

    final VirtualFile virtualFile = editor.getVirtualFile();
    if (!FlutterUtils.couldContainWidgets(virtualFile)) {
      return fliteredIndentsHighlightingPass;
    }
    final String path = virtualFile.getPath();
    final FlutterOutline outline;
    synchronized (currentOutlines) {
      outline = currentOutlines.get(path);
    }
    if (outline != null) {
      updateEditorSettings(editor);
      ApplicationManager.getApplication().invokeLater(() -> {
        runWidgetIndentsPass(editor, outline);
      });
    }
    // Return the indent pass rendering regular indent guides with guides that
    // intersect with the widget guides filtered out.
    return fliteredIndentsHighlightingPass;
  }

  void runWidgetIndentsPass(EditorEx editor, @NotNull FlutterOutline outline) {
    if (editor.isDisposed()) {
      // The editor might have been disposed before we got a new FlutterOutline.
      // It is safe to ignore it as it isn't relevant.
      return;
    }

    // If the editor and the outline have different lengths then
    // the outline is out of date and cannot safely be displayed.
    if (editor.getDocument().getTextLength() != outline.getLength() &&
        // Workaround windows bug where the outline and document content have inconsistent lengths until the file is modified.
        editor.getDocument().getModificationStamp() != 0) {

      // Outline is out of date. That is ok. Ignore it for now.
      // An up to date outline will probably arive shortly. Showing an
      // outline from data inconsistent with the current
      // content will show annoying flicker. It is better to
      // instead
      return;
    }

    WidgetIndentsHighlightingPass.run(project, editor, outline);
  }

  @Override
  public void dispose() {
    clearListeners();
    FlutterSettings.getInstance().removeListener(settingsListener);
  }
}

/**
 * Highlighing pass used as a placeholder when WidgetIndentPass was enabled
 * and then later disabled.
 * <p>
 * This is required as a TextEditorHighlightingPassFactory cannot return null.
 */
class PlaceholderHighlightingPass extends TextEditorHighlightingPass {
  PlaceholderHighlightingPass(Project project, Document document, boolean isRunIntentionPassAfter) {
    super(project, document, isRunIntentionPassAfter);
  }

  public void doCollectInformation(@NotNull ProgressIndicator indicator) {
  }

  @Override
  public void doApplyInformationToEditor() {
  }
}

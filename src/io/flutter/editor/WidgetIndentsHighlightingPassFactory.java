/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.editor;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.FlutterUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorGroupManagerService;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  private final InspectorGroupManagerService inspectorGroupManagerService;
  private final EditorMouseEventService editorEventService;
  private final EditorPositionService editorPositionService;
  private final ActiveEditorsOutlineService editorOutlineService;
  private final SettingsListener settingsListener;
  private final ActiveEditorsOutlineService.Listener outlineListener;
  protected InspectorService inspectorService;

  // Current configuration settings used to display Widget Indent Guides cached from the FlutterSettings class.
  private boolean isShowBuildMethodGuides;

  public WidgetIndentsHighlightingPassFactory(@NotNull Project project) {
    this.project = project;
    flutterDartAnalysisService = FlutterDartAnalysisServer.getInstance(project);
    this.editorOutlineService = ActiveEditorsOutlineService.getInstance(project);
    this.inspectorGroupManagerService = InspectorGroupManagerService.getInstance(project);
    this.editorEventService = EditorMouseEventService.getInstance(project);
    this.editorPositionService = EditorPositionService.getInstance(project);
    this.settingsListener = new SettingsListener();
    this.outlineListener = this::updateEditor;

    TextEditorHighlightingPassRegistrar.getInstance(project)
      .registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false);

    syncSettings(FlutterSettings.getInstance());
    FlutterSettings.getInstance().addListener(settingsListener);

    final EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        final Editor editor = event.getEditor();
        if (editor.getProject() != project) return;
        if (editor.isDisposed() || project.isDisposed()) return;
        if (!(editor instanceof EditorEx)) return;
        final EditorEx editorEx = (EditorEx)editor;
        WidgetIndentsHighlightingPass.onCaretPositionChanged(editorEx, event.getCaret());
      }
    }, this);
    editorOutlineService.addListener(outlineListener);
  }

  /**
   * Updates all editors if the settings have changed.
   *
   * <p>
   * This is useful for adding the guides in after they were turned on from the settings menu.
   */
  private void syncSettings(FlutterSettings settings) {
    if (isShowBuildMethodGuides != settings.isShowBuildMethodGuides()) {
      isShowBuildMethodGuides = settings.isShowBuildMethodGuides();
      updateAllEditors();
    }
  }

  /**
   * Updates the indent guides in the editor for the file at {@param path}.
   */
  private void updateEditor(@NotNull final String path, @Nullable FlutterOutline outline) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) {
        return;
      }
      for (EditorEx editor : editorOutlineService.getActiveDartEditors()) {
        final String filePath = editor.getVirtualFile().getCanonicalPath();
        if (!editor.isDisposed() && Objects.equals(filePath, path)) {
          runWidgetIndentsPass(editor, outline);
        }
      }
    });
  }

  // Updates all editors instead of just a specific editor.
  private void updateAllEditors() {
    ApplicationManager.getApplication().invokeLater(() -> {
      // Find visible editors for the path. If the file is not actually
      // being displayed on screen, there is no need to go through the
      // work of updating the outline.
      if (project.isDisposed()) {
        return;
      }
      for (EditorEx editor : editorOutlineService.getActiveDartEditors()) {
        if (!editor.isDisposed()) {
          runWidgetIndentsPass(editor, editorOutlineService.getOutline(editor.getVirtualFile().getCanonicalPath()));
        }
      }
    });
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
        ApplicationManager.getApplication().invokeLater(() -> WidgetIndentsHighlightingPass.cleanupHighlighters(e));
      }

      // Return a placeholder editor highlighting pass. The user will get the
      // regular IntelliJ platform provided FilteredIndentsHighlightingPass in this case.
      // This is the special case where the user disabled the
      // WidgetIndentsGuides after previously having them setup.
      return new PlaceholderHighlightingPass(
        project,
        e.getDocument(),
        false
      );
    }
    final FilteredIndentsHighlightingPass filteredIndentsHighlightingPass = new FilteredIndentsHighlightingPass(project, e, file);
    if (!(e instanceof EditorEx)) return filteredIndentsHighlightingPass;
    final EditorEx editor = (EditorEx)e;

    final VirtualFile virtualFile = editor.getVirtualFile();
    if (!FlutterUtils.couldContainWidgets(virtualFile)) {
      return filteredIndentsHighlightingPass;
    }
    final FlutterOutline outline = editorOutlineService.getOutline(virtualFile.getCanonicalPath());

    if (outline != null) {
      updateEditorSettings(editor);
      ApplicationManager.getApplication().invokeLater(() -> runWidgetIndentsPass(editor, outline));
    }
    // Return the indent pass rendering regular indent guides with guides that
    // intersect with the widget guides filtered out.
    return filteredIndentsHighlightingPass;
  }

  void runWidgetIndentsPass(EditorEx editor, FlutterOutline outline) {
    if (editor.isDisposed() || project.isDisposed()) {
      // The editor might have been disposed before we got a new FlutterOutline.
      // It is safe to ignore it as it isn't relevant.
      return;
    }

    if (!isShowBuildMethodGuides || outline == null) {
      // If build method guides are disabled or there is no outline to use in this pass,
      // then do nothing.
      return;
    }

    final VirtualFile file = editor.getVirtualFile();
    if (!FlutterUtils.couldContainWidgets(file)) {
      return;
    }
    // If the editor and the outline have different lengths then
    // the outline is out of date and cannot safely be displayed.
    final DocumentEx document = editor.getDocument();
    final int documentLength = document.getTextLength();
    final int outlineLength = outline.getLength();
    // TODO(jacobr): determine why we sometimes have to check against both the
    // raw outlineLength and the converted outline length for things to work
    // correctly on windows.
    if (documentLength != outlineLength &&
        documentLength != DartAnalysisServerService.getInstance(project).getConvertedOffset(file, outlineLength)) {
      // Outline is out of date. That is ok. Ignore it for now.
      // An up to date outline will probably arrive shortly. Showing an
      // outline from data inconsistent with the current
      // content will show annoying flicker. It is better to
      // instead
      return;
    }
    // We only need to convert offsets when the document and outline disagree
    // on the document length.
    final boolean convertOffsets = documentLength != outlineLength;

    WidgetIndentsHighlightingPass.run(
      project,
      editor,
      outline,
      flutterDartAnalysisService,
      inspectorGroupManagerService,
      editorEventService,
      editorPositionService,
      convertOffsets
    );
  }

  @Override
  public void dispose() {
    FlutterSettings.getInstance().removeListener(settingsListener);
    editorOutlineService.removeListener(outlineListener);
  }

  // Listener that asks for another pass when the Flutter settings for indent highlighting change.
  private class SettingsListener implements FlutterSettings.Listener {
    @Override
    public void settingsChanged() {
      if (project.isDisposed()) {
        return;
      }
      final FlutterSettings settings = FlutterSettings.getInstance();
      // Skip if none of the settings that impact Widget Idents were changed.
      if (isShowBuildMethodGuides == settings.isShowBuildMethodGuides()) {
        // Change doesn't matter for us.
        return;
      }
      syncSettings(settings);

      for (EditorEx editor : editorOutlineService.getActiveDartEditors()) {
        updateEditorSettings(editor);
        // To be safe, avoid rendering artfacts when settings were changed
        // that only impacted rendering.
        editor.repaint(0, editor.getDocument().getTextLength());
      }
    }
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

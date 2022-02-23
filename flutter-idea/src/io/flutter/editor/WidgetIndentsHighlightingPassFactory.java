/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.application.options.editor.EditorOptionsListener;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.EdtInvocationManager;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.FlutterUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.InspectorGroupManagerService;
import io.flutter.inspector.InspectorService;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Factory that drives all rendering of widget indents.
 *
 * Warning: it is unsafe to register the WidgetIndentsHighlightingPassFactory
 * without using WidgetIndentsHighlightingPassFactoryRegistrar as recent
 * versions of IntelliJ will unpredictably clear out all existing highlighting
 * pass factories and then rerun all registrars.
 */
public class WidgetIndentsHighlightingPassFactory implements TextEditorHighlightingPassFactory, Disposable, DumbAware {
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
  private final FlutterSettings.Listener settingsListener;
  private final ActiveEditorsOutlineService.Listener outlineListener;
  protected InspectorService inspectorService;

  // Current configuration settings used to display Widget Indent Guides cached from the FlutterSettings class.
  private boolean isShowBuildMethodGuides;
  // Current configuration setting used to display regular indent guides cached from EditorSettingsExternalizable.
  private boolean isIndentGuidesShown;

  public static WidgetIndentsHighlightingPassFactory getInstance(Project project) {
    return ServiceManager.getService(project, WidgetIndentsHighlightingPassFactory.class);
  }

  public WidgetIndentsHighlightingPassFactory(@NotNull Project project) {
    this.project = project;
    flutterDartAnalysisService = FlutterDartAnalysisServer.getInstance(project);
    // TODO(jacobr): I'm not clear which Disposable it is best to tie the
    // lifecycle of this object. The FlutterDartAnalysisServer is chosen at
    // random as a Disposable with generally the right lifecycle. IntelliJ
    // returns a lint warning if you tie the lifecycle to the Project.
    this.editorOutlineService = ActiveEditorsOutlineService.getInstance(project);
    this.inspectorGroupManagerService = InspectorGroupManagerService.getInstance(project);
    this.editorEventService = EditorMouseEventService.getInstance(project);
    this.editorPositionService = EditorPositionService.getInstance(project);
    this.settingsListener = this::onSettingsChanged;
    this.outlineListener = this::updateEditor;
    syncSettings(FlutterSettings.getInstance());
    FlutterSettings.getInstance().addListener(settingsListener);
    isIndentGuidesShown = EditorSettingsExternalizable.getInstance().isIndentGuidesShown();
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
      EditorOptionsListener.APPEARANCE_CONFIGURABLE_TOPIC, () -> {
        final boolean newValue = EditorSettingsExternalizable.getInstance().isIndentGuidesShown();
        if (isIndentGuidesShown != newValue) {
          isIndentGuidesShown = newValue;
          updateAllEditors();
        }
      });

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

  @Nullable
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor e) {
    // Surprisingly, the highlighting pass returned by this method isn't the
    // highlighting pass to display the indent guides. It is the highlighting
    // pass that display regular indent guides for Dart files that filters out
    // indent guides that intersect with the widget indent guides. because
    // the widget indent guides are powered by the Dart Analyzer, computing the
    // widget indent guides themselves must be done asynchronously driven by
    // analysis server updates not IntelliJ's default assumptions about how a
    // text highlighting pass should work. See runWidgetIndentsPass for the
    // logic that handles the actual widget indent guide pass.
    if (file.getVirtualFile() == null) return null;
    if (!FlutterUtils.isDartFile(file.getVirtualFile())) {
      return null;
    }

    if (!isShowBuildMethodGuides) {
      // Reset the editor back to its default indent guide setting as build
      // method guides are disabled and the file is a dart file.
      e.getSettings().setIndentGuidesShown(isIndentGuidesShown);
      // Cleanup custom filtered build method guides that may be left around
      // from when our custom filtered build method guides were previously
      // shown. This cleanup is very cheap if it has already been performed
      // so there is no harm in performing it more than once.
      FilteredIndentsHighlightingPass.cleanupHighlighters(e);
      return null;
    }
    // If we are showing build method guides we can never show the regular
    // IntelliJ indent guides for a file because they will overlap with the
    // widget indent guides in distracting ways.
    if (EdtInvocationManager.getInstance().isEventDispatchThread()) {
      e.getSettings().setIndentGuidesShown(false);
    } else {
      ApplicationManager.getApplication().invokeLater(() -> {
        e.getSettings().setIndentGuidesShown(false);
      });
    }
    // If regular indent guides should be shown we need to show the filtered
    // indents guides which look like the regular indent guides except that
    // guides that intersect with the widget guides are filtered out.
    final TextEditorHighlightingPass highlightingPass;
    if (isIndentGuidesShown) {
      highlightingPass = new FilteredIndentsHighlightingPass(project, e, file);
    }
    else {
      highlightingPass = null;
      // The filtered pass might have been shown before in which case we need to clean it up.
      // Cleanup custom filtered build method guides that may be left around
      // from when our custom filtered build method guides were previously
      // shown. This cleanup is very cheap if it has already been performed
      // so there is no harm in performing it more than once.
      FilteredIndentsHighlightingPass.cleanupHighlighters(e);
    }

    if (!(e instanceof EditorEx)) return highlightingPass;
    final EditorEx editor = (EditorEx)e;

    final VirtualFile virtualFile = editor.getVirtualFile();
    if (!FlutterUtils.couldContainWidgets(virtualFile)) {
      return highlightingPass;
    }
    final FlutterOutline outline = editorOutlineService.getOutline(virtualFile.getCanonicalPath());

    if (outline != null) {
      ApplicationManager.getApplication().invokeLater(() -> runWidgetIndentsPass(editor, outline));
    }

    return highlightingPass;
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
      WidgetIndentsHighlightingPass.cleanupHighlighters(editor);
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

  void onSettingsChanged() {
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
  }

  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar) {
    registrar
      .registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false);
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.FlutterUtils;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Service that watches for {@link FlutterOutline}s for all active editors containing Dart files.
 *
 * <p>
 * This service works by listening to the {@link Project}'s MessageBus for {@link FileEditor}s that are
 * added or removed. Using the set of currently active {@link EditorEx} editor windows, this service
 * then subscribes to the {@link FlutterDartAnalysisServer} for updates to the {@link FlutterOutline} of each file.
 *
 * <p>
 * This class provides a {@link Listener} that notifies consumers when
 * <ul>
 * <li>The collection of currently active editors has changed</li>
 * <li>Each outline for a currently active editor has updated.</li>
 * </ul>
 */
public class ActiveEditorsOutlineService implements Disposable {
  private final Project project;
  private final FlutterDartAnalysisServer analysisServer;

  /**
   * Outlines for the currently visible files.
   */
  private final Map<String, FlutterOutline> pathToOutline = new HashMap<>();
  /**
   * Outline listeners for the currently visible files.
   */
  private final Map<String, FlutterOutlineListener> outlineListeners = new HashMap<>();

  /**
   * List of listeners.
   */
  private final Set<Listener> listeners = new HashSet<>();

  @NotNull
  public static ActiveEditorsOutlineService getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, ActiveEditorsOutlineService.class);
  }

  public ActiveEditorsOutlineService(Project project) {
    this(project, FlutterDartAnalysisServer.getInstance(project));
  }

  @VisibleForTesting
  ActiveEditorsOutlineService(Project project, FlutterDartAnalysisServer analysisServer) {
    this.project = project;
    this.analysisServer = analysisServer;
    updateActiveEditors();
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        updateActiveEditors();
      }
    });
  }

  /**
   * Gets all of the {@link EditorEx} editors open to Dart files.
   */
  public List<EditorEx> getActiveDartEditors() {
    if (project.isDisposed()) {
      return Collections.emptyList();
    }
    final FileEditor[] editors = FileEditorManager.getInstance(project).getSelectedEditors();
    final List<EditorEx> dartEditors = new ArrayList<>();
    for (FileEditor fileEditor : editors) {
      if (!(fileEditor instanceof TextEditor)) continue;
      final TextEditor textEditor = (TextEditor)fileEditor;
      final Editor editor = textEditor.getEditor();
      if (editor instanceof EditorEx && !editor.isDisposed()) {
        dartEditors.add((EditorEx)editor);
      }
    }
    return dartEditors;
  }

  private void updateActiveEditors() {
    if (project.isDisposed()) {
      return;
    }

    final VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    final VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();

    final Set<String> newPaths = new HashSet<>();
    for (VirtualFile file : files) {
      if (FlutterUtils.isDartFile(file)) {
        newPaths.add(file.getCanonicalPath());
      }
    }

    // Remove obsolete outline listeners.
    final List<String> obsoletePaths = new ArrayList<>();
    synchronized (outlineListeners) {
      for (final String path : outlineListeners.keySet()) {
        if (!newPaths.contains(path)) {
          obsoletePaths.add(path);
        }
      }
      for (final String path : obsoletePaths) {
        final FlutterOutlineListener listener = outlineListeners.remove(path);
        if (listener != null) {
          analysisServer.removeOutlineListener(path, listener);
        }
      }

      // Register new outline listeners.
      for (final String path : newPaths) {
        if (outlineListeners.containsKey(path)) continue;
        final FlutterOutlineListener listener = new OutlineListener(path);

        outlineListeners.put(path, listener);
        analysisServer.addOutlineListener(FileUtil.toSystemDependentName(path), listener);
      }
    }

    synchronized (pathToOutline) {
      for (final String path : obsoletePaths) {
        // Clear the current outline as it may become out of date before the
        // file is visible again.
        pathToOutline.remove(path);
      }
    }
  }

  private void notifyOutlineUpdated(String path) {
    ArrayList<Listener> listenerList;
    synchronized (listeners) {
      listenerList = Lists.newArrayList(listeners);
    }
    for (Listener listener : listenerList) {
      listener.onOutlineChanged(path, getOutline(path));
    }
  }

  public void addListener(@NotNull Listener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  public void removeListener(@NotNull Listener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  /**
   * Gets the most up-to-date {@link FlutterOutline} for the file at {@param path}.
   * <p>
   * To get an outline that is guaranteed in-sync with the file it outlines, see {@link #getIfUpdated}.
   */
  @Nullable
  public FlutterOutline getOutline(String path) {
    return pathToOutline.get(path);
  }

  /**
   * Gets the {@link FlutterOutline} for {@param file} if and only if the outline is up to date with the file.
   *
   * <p>
   * Returns null if the file is out of date.
   */
  @Nullable
  public FlutterOutline getIfUpdated(@NotNull PsiFile file) {
    final FlutterOutline outline = getOutline(file.getVirtualFile().getCanonicalPath());
    if (isOutdated(outline, file)) {
      return null;
    }
    return outline;
  }

  /**
   * Checks that the {@param outline} matches the current version of {@param file}.
   *
   * <p>
   * An outline and file match if they have the same length.
   */
  private boolean isOutdated(@Nullable FlutterOutline outline, @NotNull PsiFile file) {
    final DartAnalysisServerService das = DartAnalysisServerService.getInstance(file.getProject());
    if (outline == null) {
      return true;
    }
    return file.getTextLength() != outline.getLength()
           && file.getTextLength() != das.getConvertedOffset(file.getVirtualFile(), outline.getLength());
  }

  @Override
  public void dispose() {
    synchronized (outlineListeners) {
      final Iterator<Map.Entry<String, FlutterOutlineListener>> iterator = outlineListeners.entrySet().iterator();

      while (iterator.hasNext()) {
        final Map.Entry<String, FlutterOutlineListener> entry = iterator.next();

        final String path = entry.getKey();
        final FlutterOutlineListener listener = entry.getValue();

        iterator.remove();

        if (listener != null) {
          analysisServer.removeOutlineListener(path, listener);
        }
      }

      outlineListeners.clear();
    }

    synchronized (pathToOutline) {
      pathToOutline.clear();
    }

    synchronized (listeners) {
      listeners.clear();
    }
  }

  /**
   * Listener for changes to the active editors or open outlines.
   */
  public interface Listener {
    /**
     * Called on a change in the {@link FlutterOutline} of file at {@param filePath}.
     */
    void onOutlineChanged(@NotNull String filePath, @Nullable FlutterOutline outline);
  }

  /**
   * Listener called by the {@link FlutterDartAnalysisServer} when an open file's outline changes.
   *
   * <p>
   * This class caches the updated outline inside {@link ActiveEditorsOutlineService#pathToOutline} for the file.
   */
  private class OutlineListener implements FlutterOutlineListener {
    private final String path;

    OutlineListener(String path) {
      this.path = path;
    }

    @Override
    public void outlineUpdated(@NotNull String systemDependentPath,
                               @NotNull FlutterOutline outline,
                               @Nullable String instrumentedCode) {
      // Avoid using the path return by the FlutterOutline service as it will
      // be system dependent causing bugs on windows.
      synchronized (outlineListeners) {
        if (!outlineListeners.containsKey(path)) {
          // The outline listener subscription was already cancelled.
          return;
        }
      }
      synchronized (pathToOutline) {
        pathToOutline.put(path, outline);
        notifyOutlineUpdated(path);
      }
    }
  }
}

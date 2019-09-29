/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

public class EditorEventServiceBase<L> implements Disposable {
  protected interface InvokeListener<L> {
    void run(L listener);
  }

  private final SetMultimap<EditorEx, L> listeners = HashMultimap.create();
  private final Project project;

  public EditorEventServiceBase(Project project) {
    this.project = project;
  }

  protected void invokeAll(InvokeListener<L> invoke, Editor editor) {
    if (!(editor instanceof EditorEx)) {
      return;
    }
    final ArrayList<EditorEx> disposedEditors = new ArrayList<>();
    for (EditorEx e : listeners.keySet()) {
      if (e.isDisposed()) {
        disposedEditors.add(e);
      }
    }
    for (EditorEx e : disposedEditors) {
      listeners.removeAll(e);
    }
    final EditorEx editorEx = (EditorEx)editor;
    final Set<L> matches = listeners.get(editorEx);
    if (matches == null || matches.isEmpty()) return;
    for (L listener : matches) {
      try {
        invoke.run(listener);
      }
      catch (Exception e) {
        // XXX log.
      }
    }
  }

  public void addListener(@NotNull EditorEx editor, @NotNull L listener) {
    synchronized (listeners) {
      listeners.put(editor, listener);
    }
  }

  public void removeListener(@NotNull EditorEx editor, @NotNull L listener) {
    synchronized (listeners) {
      listeners.remove(editor, listener);
    }
  }

  EditorEx getIfValidForProject(Editor editor) {
    if (editor.getProject() != project) return null;
    if (editor.isDisposed() || project.isDisposed()) return null;
    if (!(editor instanceof EditorEx)) return null;
    return (EditorEx)editor;
  }

  @Override
  public void dispose() {
    listeners.clear();
  }
}

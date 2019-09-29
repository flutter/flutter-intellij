/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Service that tracks the position and Carat selection of an editor
 * <p>
 * This class provides a {@link EditorPositionService.Listener} that notifies consumers when
 * the editor changes position or carat selection.
 */

public class EditorPositionService extends EditorEventServiceBase<EditorPositionService.Listener> implements Disposable {

  public interface Listener {
    void updateVisibleArea(Rectangle newRectangle);

    void onVisibleChanged();

    void updateSelected(Caret carat);
  }

  private final VisibleAreaListener visibleAreaListener;
  private final EditorEventMulticaster eventMulticaster;

  public EditorPositionService(Project project) {
    super(project);

    eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    visibleAreaListener = (VisibleAreaEvent event) -> {
      invokeAll(listener -> listener.updateVisibleArea(event.getNewRectangle()), event.getEditor());
    };
    eventMulticaster.addVisibleAreaListener(visibleAreaListener);

    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        invokeAll(listener -> listener.updateSelected(e.getCaret()), e.getEditor());
      }
    }, this);
    // TODO(jacobr): listen for when editors are disposed?
  }

  @NotNull
  public static EditorPositionService getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, EditorPositionService.class);
  }

  public void addListener(EditorEx editor, @NotNull Listener listener) {
    super.addListener(editor, listener);
    // Notify the listener of the current state.
    listener.updateVisibleArea(editor.getScrollingModel().getVisibleArea());
    final Caret carat = editor.getCaretModel().getPrimaryCaret();
    if (carat.isValid()) {
      listener.updateSelected(carat);
    }
    else {
      System.out.println("XXX no valid carat");
    }
  }

  @Override
  public void dispose() {
    eventMulticaster.removeVisibleAreaListener(visibleAreaListener);
    super.dispose();
  }
}

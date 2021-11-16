/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Service that tracks interactions with editors making it easy to add
 * listeners that are notified when interactions with editors occur.
 * <p>
 * This class provides a {@link EditorMouseEventService.Listener} that notifies consumers when
 * mouse events and selection events occur.
 */

public class EditorMouseEventService extends EditorEventServiceBase<EditorMouseEventService.Listener> implements Disposable {

  public interface Listener {
    void onMouseMoved(MouseEvent event);

    void onMousePressed(MouseEvent event);

    void onMouseReleased(MouseEvent event);

    void onMouseEntered(MouseEvent event);

    void onMouseExited(MouseEvent event);
  }

  private final EditorEventMulticaster eventMulticaster;
  private final EditorMouseMotionListener mouseMotionListener;

  public EditorMouseEventService(Project project) {
    super(project);

    eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    mouseMotionListener = new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull EditorMouseEvent e) {
        invokeAll(listener -> listener.onMouseMoved(e.getMouseEvent()), e.getEditor());
      }
    };
    eventMulticaster.addEditorMouseMotionListener(mouseMotionListener);
    eventMulticaster.addEditorMouseListener(new EditorMouseListener() {
      @Override
      public void mousePressed(@NotNull EditorMouseEvent e) {
        invokeAll(listener -> listener.onMousePressed(e.getMouseEvent()), e.getEditor());
      }

      @Override
      public void mouseReleased(@NotNull EditorMouseEvent e) {
        invokeAll(listener -> listener.onMouseReleased(e.getMouseEvent()), e.getEditor());
      }


      @Override
      public void mouseEntered(@NotNull EditorMouseEvent e) {
        invokeAll(listener -> listener.onMouseEntered(e.getMouseEvent()), e.getEditor());
      }

      @Override
      public void mouseExited(@NotNull EditorMouseEvent e) {
        invokeAll(listener -> listener.onMouseExited(e.getMouseEvent()), e.getEditor());
      }
    }, this);
    // TODO(jacobr): listen for when editors are disposed?
  }

  @NotNull
  public static EditorMouseEventService getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, EditorMouseEventService.class);
  }

  @Override
  public void dispose() {
    super.dispose();
    eventMulticaster.removeEditorMouseMotionListener(mouseMotionListener);
  }
}

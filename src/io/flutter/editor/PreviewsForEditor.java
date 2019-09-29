/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class PreviewsForEditor implements WidgetViewModeInterface, Disposable {

  private final EditorMouseEventService editorEventService;
  private Balloon popup;

  static final boolean showOverallPreview = false;
  boolean isDisposed = false;

  public PreviewsForEditor(WidgetEditingContext data, EditorMouseEventService editorEventService) {
    this.data = data;
    this.editorEventService = editorEventService;
    previews = new ArrayList<>();
    if (showOverallPreview) {
      overallPreview = new PreviewViewController(new WidgetViewModelData(null, null, data));
    } else {
      overallPreview = null;
    }
    final Project project = data.editor.getProject();
    editorEventService.addListener(data.editor,this);
  }

  @Override
  public void dispose() {
    if (isDisposed) return;
    
    if (editorEventService != null && data.editor != null) {
      editorEventService.removeListener(data.editor, this);
      if (popup != null && !popup.isDisposed()) {
        popup.dispose();
      }
    }
    for (PreviewViewController preview : getAllPreviews(false)) {
      preview.dispose();
    }
    previews = null;
    overallPreview = null;
    isDisposed = true;
  }

  private final WidgetEditingContext data;
  private ArrayList<PreviewViewController> previews;

  private PreviewViewController overallPreview;

  public void outlinesChanged(Iterable<WidgetIndentGuideDescriptor> newDescriptors) {
    if (1 == 1) {
      // XXX SKIP previews to debug outline.
      return;
    }
    final ArrayList<PreviewViewController> newPreviews = new ArrayList<>();
    boolean changed = false;

    int i = 0;
    // TODO(jacobr): be smarter about reusing.
    for (WidgetIndentGuideDescriptor descriptor : newDescriptors) {
      if (descriptor.parent == null) {
        if (i >= previews.size() || !descriptor.equals(previews.get(i).getDescriptor())) {
          newPreviews.add(new PreviewViewController(new WidgetViewModelData(descriptor, null, data)));
          changed = true;
        } else {
          newPreviews.add(previews.get(i));
          i++;
        }
      }
    }
    while ( i < previews.size()) {
      changed = true;
      previews.get(i).dispose();
      i++;
    }
    previews = newPreviews;
  }

  private Iterable<PreviewViewController> getAllPreviews(boolean paintOrder) {
    final ArrayList<PreviewViewController> all = new ArrayList<>();
    if (overallPreview != null) {
      all.add(overallPreview);
    }
    all.addAll(previews);
    if (paintOrder ) {
      all.sort((a, b) -> { return Integer.compare(a.getPriority(), b.getPriority());});
    } else {
      all.sort((a, b) -> {
        return Integer.compare(b.getPriority(), a.getPriority());
      });
    }
    return all;
  }

  @Override
  public void onMouseMoved(MouseEvent event) {
    for (PreviewViewController preview : getAllPreviews(false)) {
      if (event.isConsumed()) {
        preview.onMouseExited(event);
      } else {
        preview.onMouseMoved(event);
      }
    }
  }

  @Override
  public void onMousePressed(MouseEvent event) {
    for (PreviewViewController preview : getAllPreviews(false)) {
      preview.onMousePressed(event);
      if (event.isConsumed()) break;
    }
    // XXX this appears to be duplicated with the viewModel code.
    /* XXX
    if (!event.isConsumed() && event.isShiftDown()) {
      event.consume();
      final LogicalPosition logicalPosition = data.editor.xyToLogicalPosition(event.getPoint());
      System.out.println("XXX logicalPosition = " + logicalPosition);

      XSourcePositionImpl position = XSourcePositionImpl.create(data.editor.getVirtualFile(), logicalPosition.line, logicalPosition.column);
      Point point = event.getLocationOnScreen();

      if (popup != null) {
        popup.dispose();;
        popup = null;
      }
      popup = PropertyEditorPanel.showPopup(getApp(), data.editor, null, position, data.flutterDartAnalysisService, point);
    } else {
      if (popup != null) {
        popup.dispose();
      }
    }*/
  }

  @Override
  public void onMouseReleased(MouseEvent event) {
    for (PreviewViewController preview : getAllPreviews(false)) {
      preview.onMouseReleased(event);
      if (event.isConsumed()) break;
    }
  }

  @Override
  public void onMouseEntered(MouseEvent event) {
    for (PreviewViewController preview : getAllPreviews(false)) {
      if (event.isConsumed()) {
        preview.onMouseExited(event);
      } else {
        preview.onMouseEntered(event);
      }
    }
  }

  @Override
  public void onMouseExited(MouseEvent event) {
    for (PreviewViewController preview : getAllPreviews(false)) {
      preview.onMouseExited(event);
    }
  }

  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics graphics) {
    for (PreviewViewController preview : getAllPreviews(true)) {
      if (preview.visible) {
        preview.paint(editor, highlighter, graphics);
      }
    }
  }
}

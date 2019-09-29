/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Class that manages rendering screenshots of all build methods in a file
 * directly in the text editor.
 */
public class PreviewsForEditor implements CustomHighlighterRenderer {
  static final boolean showOverallPreview = false;
  private final EditorEx editor;
  private final Disposable parentDisposable;
  private final WidgetEditingContext data;
  private final InlinePreviewViewController overallPreview;
  private ArrayList<InlinePreviewViewController> previews;

  public PreviewsForEditor(WidgetEditingContext data,
                           EditorMouseEventService editorEventService,
                           EditorEx editor,
                           Disposable parentDisposable) {
    this.data = data;
    this.editor = editor;
    this.parentDisposable = parentDisposable;
    previews = new ArrayList<>();
    if (showOverallPreview) {
      overallPreview = new InlinePreviewViewController(
        new InlineWidgetViewModelData(null, editor, data),
        true,
        parentDisposable
      );
    }
    else {
      overallPreview = null;
    }
    editorEventService.addListener(
      editor,
      new EditorMouseEventService.Listener() {

        @Override
        public void onMouseMoved(MouseEvent event) {
          for (PreviewViewControllerBase preview : getAllPreviews(false)) {
            if (event.isConsumed()) {
              preview.onMouseExited(event);
            }
            else {
              preview.onMouseMoved(event);
            }
          }
        }

        @Override
        public void onMousePressed(MouseEvent event) {
          for (PreviewViewControllerBase preview : getAllPreviews(false)) {
            preview.onMousePressed(event);
            if (event.isConsumed()) break;
          }
        }

        @Override
        public void onMouseReleased(MouseEvent event) {
          for (PreviewViewControllerBase preview : getAllPreviews(false)) {
            preview.onMouseReleased(event);
            if (event.isConsumed()) break;
          }
        }

        @Override
        public void onMouseEntered(MouseEvent event) {
          for (PreviewViewControllerBase preview : getAllPreviews(false)) {
            if (event.isConsumed()) {
              preview.onMouseExited(event);
            }
            else {
              preview.onMouseEntered(event);
            }
          }
        }

        @Override
        public void onMouseExited(MouseEvent event) {
          for (PreviewViewControllerBase preview : getAllPreviews(false)) {
            preview.onMouseExited(event);
          }
        }
      },
      parentDisposable
    );
  }

  public void outlinesChanged(Iterable<WidgetIndentGuideDescriptor> newDescriptors) {
    final ArrayList<InlinePreviewViewController> newPreviews = new ArrayList<>();

    int i = 0;
    // TODO(jacobr): be smarter about reusing.
    for (WidgetIndentGuideDescriptor descriptor : newDescriptors) {
      if (descriptor.parent == null) {
        if (i >= previews.size() || !descriptor.equals(previews.get(i).getDescriptor())) {
          newPreviews.add(new InlinePreviewViewController(new InlineWidgetViewModelData(descriptor, editor, data), true, parentDisposable));
        }
        else {
          newPreviews.add(previews.get(i));
          i++;
        }
      }
    }
    while (i < previews.size()) {
      Disposer.dispose(previews.get(i));
      i++;
    }
    previews = newPreviews;
  }

  protected Iterable<InlinePreviewViewController> getAllPreviews(boolean paintOrder) {
    final ArrayList<InlinePreviewViewController> all = new ArrayList<>();
    if (overallPreview != null) {
      all.add(overallPreview);
    }
    all.addAll(previews);
    if (paintOrder) {
      all.sort(Comparator.comparingInt(InlinePreviewViewController::getPriority));
    }
    else {
      all.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }
    return all;
  }

  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics graphics) {
    for (InlinePreviewViewController preview : getAllPreviews(true)) {
      if (preview.visible) {
        preview.paint(editor, highlighter, graphics);
      }
    }
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.Screenshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * PreviewViewController that renders inline in a code editor.
 */
public class InlinePreviewViewController extends PreviewViewControllerBase implements CustomHighlighterRenderer {

  protected float previewWidthScale = 0.7f;

  public InlinePreviewViewController(InlineWidgetViewModelData data, boolean drawBackground, Disposable disposable) {
    super(data, drawBackground, disposable);
    data.context.editorPositionService.addListener(getEditor(), new EditorPositionService.Listener() {
      @Override
      public void updateVisibleArea(Rectangle newRectangle) {
        InlinePreviewViewController.this.updateVisibleArea(newRectangle);
      }

      @Override
      public void onVisibleChanged() {
        InlinePreviewViewController.this.onVisibleChanged();
      }
    },
   this);
  }

  InlineWidgetViewModelData getData() {
    return (InlineWidgetViewModelData) data;
  }

  protected EditorEx getEditor() {
    return getData().editor;
  }

  public Point offsetToPoint(int offset) {
    return getEditor().visualPositionToXY(getEditor().offsetToVisualPosition(offset));
  }

  @Override
  public void forceRender() {
    if (!visible) return;

    getEditor().getComponent().repaint();
    // TODO(jacobr): consider forcing a repaint of just a fraction of the
    // editor window instead.
    // For example, if the marker corresponded to just the lines in the text
    // editor that the preview is rendereded on, we could do the following:

    // final TextRange marker = data.getMarker();
    // if (marker == null) return;
    //
    // data.editor.repaint(marker.getStartOffset(), marker.getEndOffset());
  }

  InspectorService.Location getLocation() {
    final WidgetIndentGuideDescriptor descriptor = getDescriptor();
    if (descriptor == null || descriptor.widget == null) return null;

    return InspectorService.Location.outlineToLocation(getEditor(), descriptor.outlineNode);
  }

  public WidgetIndentGuideDescriptor getDescriptor() { return getData().descriptor; }

  @Override
  public void computeScreenshotBounds() {
    final Rectangle previousScreenshotBounds = screenshotBounds;
    screenshotBounds = null;
    maxHeight = Math.round(PREVIEW_MAX_HEIGHT * 0.16f);
    final WidgetIndentGuideDescriptor descriptor = getDescriptor();

    final int lineHeight = getEditor() != null ? getEditor().getLineHeight() : defaultLineHeight;
    extraHeight = descriptor != null && screenshot != null ? lineHeight: 0;

    final Rectangle visibleRect = this.visibleRect;
    if (visibleRect == null) {
      return;
    }

    if (descriptor == null) {
      // Special case to float in the bottom right corner.
      final Screenshot latestScreenshot = getScreenshotNow();
      int previewWidth = Math.round(PREVIEW_MAX_WIDTH * previewWidthScale);
      int previewHeight = Math.round((PREVIEW_MAX_HEIGHT * 0.16f) * previewWidthScale);
      if (latestScreenshot != null) {
        previewWidth = (int)(latestScreenshot.image.getWidth() / getDPI());
        previewHeight = (int)(latestScreenshot.image.getHeight() / getDPI());
      }
      final int previewStartX = Math.max(0, visibleRect.x + visibleRect.width - previewWidth - PREVIEW_PADDING_X);
      previewHeight = Math.min(previewHeight, visibleRect.height);

      maxHeight = visibleRect.height;
      final int previewStartY = Math.max(visibleRect.y, visibleRect.y + visibleRect.height - previewHeight);
      screenshotBounds = new Rectangle(previewStartX, previewStartY, previewWidth, previewHeight);
      return;
    }

    final TextRange marker = getData().getMarker();
    if (marker == null) return;
    final int startOffset = marker.getStartOffset();
    final Document doc = getData().document;
    final int textLength = doc.getTextLength();
    if (startOffset >= textLength) return;

    final int endOffset = Math.min(marker.getEndOffset(), textLength);

    int off;
    final int startLine = doc.getLineNumber(startOffset);

    final int widgetOffset = getDescriptor().widget.getGuideOffset();
    final int widgetLine = doc.getLineNumber(widgetOffset);
    final int lineEndOffset = doc.getLineEndOffset(widgetLine);

    // Request a thumbnail and render it in the space available.
    VisualPosition visualPosition = getEditor().offsetToVisualPosition(lineEndOffset); // e
    visualPosition = new VisualPosition(Math.max(visualPosition.line, 0), 81);
    final Point start = getEditor().visualPositionToXY(visualPosition);
    final Point endz = offsetToPoint(endOffset);
    final int endY = endz.y;
    final int visibleEndX = visibleRect.x + visibleRect.width;
    final int width = Math.max(0, visibleEndX - 20 - start.x);
    final int height = Math.max(0, endY - start.y);
    int previewStartY = start.y;
    int previewStartX = start.x;
    final int visibleStart = visibleRect.y;
    final int visibleEnd = (int)visibleRect.getMaxY();

    // Add extra room for the descriptor.
    final Screenshot latestScreenshot = getScreenshotNow();
    int previewWidth = PREVIEW_MAX_WIDTH;
    int previewHeight = PREVIEW_MAX_HEIGHT / 6;
    if (latestScreenshot != null) {
      previewWidth = (int)(latestScreenshot.image.getWidth() / getDPI());
      previewHeight = (int)(latestScreenshot.image.getHeight() / getDPI());
    }
    previewStartX = Math.max(previewStartX, visibleEndX - previewWidth - PREVIEW_PADDING_X);
    previewHeight += extraHeight;
    previewHeight = Math.min(previewHeight, height);

    maxHeight = endz.y - start.y;
    if (popupActive()) {
      // Keep the bounds sticky maintining the same lastScreenshotBoundsWindow.
      screenshotBounds = new Rectangle(lastScreenshotBoundsWindow);
      screenshotBounds.translate(visibleRect.x, visibleRect.y);
    } else {
      boolean lockUpdate =false;
      if (isVisiblityLocked()) {
        // TODO(jacobr): also need to keep sticky if there is some minor scrolling
        if (previousScreenshotBounds != null && visibleRect.contains(previousScreenshotBounds)) {
          screenshotBounds = new Rectangle(previousScreenshotBounds);

          // Fixup if the screenshot changed
          if (previewWidth != screenshotBounds.width) {
            screenshotBounds.x += screenshotBounds.width - previewWidth;
            screenshotBounds.width = previewWidth;
          }
          screenshotBounds.height = previewHeight;

          // TODO(jacobr): refactor this code so there is less duplication.
          lastScreenshotBoundsWindow = new Rectangle(screenshotBounds);
          lastScreenshotBoundsWindow.translate(-visibleRect.x, -visibleRect.y);
          lockUpdate = true;
        }
      }

      if (!lockUpdate){
        lastLockedRectangle = null;
        if (start.y <= visibleEnd && endY >= visibleStart) {
          if (visibleStart > previewStartY) {
            previewStartY = Math.max(previewStartY, visibleStart);
            previewStartY = Math.min(previewStartY, Math.min(endY - previewHeight, visibleEnd - previewHeight));
          }
          screenshotBounds = new Rectangle(previewStartX, previewStartY, previewWidth, previewHeight);
          lastScreenshotBoundsWindow = new Rectangle(screenshotBounds);
          lastScreenshotBoundsWindow.translate(-visibleRect.x, -visibleRect.y);
        }
      }
    }

    if (popupActive()) {
      lastLockedRectangle = new Rectangle(visibleRect);
    }
  }

  @Override
  protected Dimension getPreviewSize() {
    int previewWidth;
    int previewHeight;
    previewWidth = PREVIEW_MAX_WIDTH;
    previewHeight = PREVIEW_MAX_HEIGHT;

    if (getDescriptor() == null) {
      previewWidth = Math.round(previewWidth * previewWidthScale);
      previewHeight = Math.round(previewHeight * previewWidthScale);
    }
    return new Dimension(previewWidth, previewHeight);
  }

  private void updateVisibleArea(Rectangle newRectangle) {
    visibleRect = newRectangle;
    if (getDescriptor() == null || getData().getMarker() == null) {
      if (!visible) {
        visible = true;
        onVisibleChanged();
      }
      return;
    }
    final TextRange marker = getData().getMarker();
    if (marker == null) return;

    final Point start = offsetToPoint(marker.getStartOffset());
    final Point end = offsetToPoint(marker.getEndOffset());
    final boolean nowVisible = newRectangle == null || newRectangle.y <= end.y && newRectangle.y + newRectangle.height >= start.y ||
                               updateVisiblityLocked(newRectangle);
    if (visible != nowVisible) {
      visible = nowVisible;
      onVisibleChanged();
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  protected Component getComponent() {
    return getEditor().getComponent();
  }

  @Override
  protected VirtualFile getVirtualFile() {
    return getEditor().getVirtualFile();
  }

  @Override
  protected Document getDocument() {
    return getEditor().getDocument();
  }

  @Override
  protected void showPopup(Point location, DiagnosticsNode node) {
    location =
        SwingUtilities.convertPoint(
          getEditor().getContentComponent(),
          location,
          getEditor().getComponent()
        );
    popup = PropertyEditorPanel
      .showPopup(data.context.inspectorGroupManagerService, getEditor(), node, node.getCreationLocation().getLocation(), data.context.flutterDartAnalysisService, location);
  }

  @Override
  TextRange getActiveRange() {
    return getData().getMarker();
  }

  @Override
  void setCustomCursor(@Nullable Cursor cursor) {
    if (getEditor() == null) {
      // TODO(jacobr): customize the cursor when there is not an associated editor.
      return;
    }
    getEditor().setCustomCursor(this, cursor);
  }

  // Determine zOrder of overlapping previews.
  // Ideally we should work harder to prevent overlapping.
  public int getPriority() {
    int priority = 0;
    if (popupActive()) {
      priority += 20;
    }
    if (isVisiblityLocked()) {
      priority += 10;
    }

    if (isSelected) {
      priority += 5;
    }

    if (getDescriptor() != null) {
      priority += 1;
    }

    if (screenshot == null && (elements == null || elements.isEmpty())) {
      priority -= 5;
      if (getDescriptor() != null) {
        priority -= 100;
      }
    } else {
      if (hasCurrentHits() || _mouseInScreenshot) {
        priority += 10;
      }
    }
    if (_mouseInScreenshot) {
      priority += 20;
    }
    return priority;
  }

  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
    if (editor != getEditor()) {
      // Don't want to render on the wrong editor. This shouldn't happen.
      return;
    }
    if (getEditor().isPurePaintingMode()) {
      // Don't show previews in pure mode.
      return;
    }
    if (!highlighter.isValid()) {
      return;
    }
    if (getDescriptor() != null && !getDescriptor().widget.isValid()) {
      return;
    }
    final int lineHeight = editor.getLineHeight();
    paint(g, lineHeight);
  }

  public void paint(@NotNull Graphics g, int lineHeight) {
    final Graphics2D g2d = (Graphics2D)g.create();

    final Screenshot latestScreenshot = getScreenshotNow();
    if (latestScreenshot != null) {
      final int imageWidth = (int)(latestScreenshot.image.getWidth() * getDPI());
      final int imageHeight = (int)(latestScreenshot.image.getHeight() * getDPI());
      if (extraHeight > 0) {
        if (drawBackground) {
          g2d.setColor(JBColor.LIGHT_GRAY);
          g2d.fillRect(screenshotBounds.x, screenshotBounds.y, Math.min(screenshotBounds.width, imageWidth),
                       Math.min(screenshotBounds.height, extraHeight));
        }
        final WidgetIndentGuideDescriptor descriptor = getDescriptor();
        if (descriptor != null) {
          final int line = descriptor.widget.getLine() + 1;
          final int column = descriptor.widget.getColumn() + 1;
          final int numActive = elements != null ? elements.size() : 0;
          String message = descriptor.outlineNode.getClassName() + " ";//+ " Widget ";
          if (numActive == 0) {
            message += "(inactive)";
          }
          else if (numActive > 1) {
            message += "(" + (activeIndex + 1) + " of " + numActive + ")";
          }
          if (numActive > 0 && screenshot != null && screenshot.transformedRect != null) {
            final Rectangle2D bounds = screenshot.transformedRect.getRectangle();
            final long w = Math.round(bounds.getWidth());
            final long h = Math.round(bounds.getHeight());
            message += " " + w + "x" + h;
          }

          g2d.setColor(JBColor.BLACK);
          drawMultilineString(g2d,
                              message,
                              screenshotBounds.x + 4,
                              screenshotBounds.y + lineHeight - 6, lineHeight);
        }
      }
    }
    super.paint(g, lineHeight);
  }

  @Override
  String getNoScreenshotMessage() {
    final WidgetIndentGuideDescriptor descriptor = getDescriptor();
    if (descriptor == null) {
      return super.getNoScreenshotMessage();
    }
    final int line = descriptor.widget.getLine() + 1;
    final int column = descriptor.widget.getColumn() + 1;
    return descriptor.outlineNode.getClassName() + " Widget " + line + ":" + column + "\n"+
                             "not currently active";
  }
}

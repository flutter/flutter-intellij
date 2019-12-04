/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class PreviewViewController extends PreviewViewControllerBase {

  Rectangle screenshotBoundsOverride;
  final Component component;

  public PreviewViewController(WidgetViewModelData data, boolean drawBackground, Component component, Disposable parentDisposable) {
    super(data, drawBackground, parentDisposable);
    visible = true;
    this.component = component;
  }

  public void setScreenshotBounds(Rectangle bounds) {
    final boolean sizeChanged = screenshotBounds != null && (screenshotBounds.width != bounds.width || screenshotBounds.height != bounds.height);
    screenshotBoundsOverride = bounds;
    screenshotBounds = bounds;
    if (sizeChanged) {
      // Get a new screenshot as the existing screenshot isn't the right resolution.
      // TODO(jacobr): only bother if the resolution is sufficiently different.
      fetchScreenshot(false);
    }
  }

  @Override
  protected Component getComponent() {
    return component;
  }

  @Override
  protected VirtualFile getVirtualFile() {
    return null;
  }

  @Override
  protected Document getDocument() {
    return null;
  }

  @Override
  protected void showPopup(Point location, DiagnosticsNode node) {
    popup = PropertyEditorPanel
      .showPopup(data.context.inspectorGroupManagerService, data.context.project, getComponent(), node, node.getCreationLocation().getLocation(), data.context.flutterDartAnalysisService, location);
  }

  @Override
  TextRange getActiveRange() {
    return null;
  }

  @Override
  void setCustomCursor(@Nullable Cursor cursor) {
    getComponent().setCursor(cursor);
  }

  @Override
  public void computeScreenshotBounds() {
    screenshotBounds = screenshotBoundsOverride;
  }

  @Override
  protected Dimension getPreviewSize() {
    return screenshotBoundsOverride.getSize();
  }

  @Override
  public void forceRender() {
    if (component != null) {
      component.revalidate();
      component.repaint();
    }
  }

  @Override
  InspectorService.Location getLocation() {
    return null;
  }
}

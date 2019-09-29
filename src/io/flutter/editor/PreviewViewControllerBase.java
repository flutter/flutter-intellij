/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.inspector.*;
import io.flutter.utils.AsyncRateLimiter;
import io.flutter.utils.math.Matrix4;
import io.flutter.utils.math.Vector3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static io.flutter.inspector.InspectorService.toSourceLocationUri;
import static java.lang.Math.*;

/**
 * Controller that displays interactive screen mirrors for specific widget
 * subtrees or the entire app.
 * <p>
 * This controller abstracts away from the rendering code enough that it can be
 * used both when rendering previews directly in the code editor and as part of
 * a regular a regular component.
 */
public abstract class PreviewViewControllerBase extends WidgetViewController {
  // Disable popup support initially as it isn't needed given we are showing
  // properties in the outline view.
  public static final boolean ENABLE_POPUP_SUPPORT = false;
  public static final int PREVIEW_PADDING_X = 20;
  public static final double MOUSE_FRAMES_PER_SECOND = 10.0;
  public static final double SCREENSHOT_FRAMES_PER_SECOND = 3.0;
  static final int PREVIEW_MAX_WIDTH = 280;
  static final int PREVIEW_MAX_HEIGHT = 520;
  static final Color SHADOW_COLOR = new JBColor(new Color(0, 0, 0, 64), new Color(0, 0, 0, 64));
  static final int defaultLineHeight = 20;
  protected final boolean drawBackground;
  protected Balloon popup;
  protected Point lastPoint;
  protected AsyncRateLimiter mouseRateLimiter;
  protected AsyncRateLimiter screenshotRateLimiter;
  protected boolean controlDown;
  protected ArrayList<DiagnosticsNode> currentHits;
  protected InspectorObjectGroupManager hover;
  protected InspectorService.ObjectGroup screenshotGroup;
  protected boolean screenshotDirty = false;
  protected int extraHeight = 0;
  protected boolean screenshotLoading;
  protected boolean popopOpenInProgress;
  protected boolean altDown;
  protected Screenshot screenshot;
  protected ArrayList<DiagnosticsNode> boxes;
  Rectangle relativeRect;
  Rectangle lastLockedRectangle;
  Rectangle screenshotBounds;
  // Screenshot bounds in absolute window coordinates.
  Rectangle lastScreenshotBoundsWindow;
  int maxHeight;
  boolean _mouseInScreenshot = false;

  public PreviewViewControllerBase(WidgetViewModelData data, boolean drawBackground, Disposable parent) {
    super(data, parent);
    this.drawBackground = drawBackground;
  }

  @Override
  public void dispose() {
    if (isDisposed) {
      return;
    }
    if (popup != null) {
      popup.dispose();
      popup = null;
    }
    if (hover != null) {
      hover.clear(false);
      hover = null;
    }
    screenshot = null;
    super.dispose();
  }

  AsyncRateLimiter getMouseRateLimiter() {
    if (mouseRateLimiter != null) return mouseRateLimiter;
    mouseRateLimiter = new AsyncRateLimiter(MOUSE_FRAMES_PER_SECOND, () -> {
      return updateMouse(false);
    }, this);
    return mouseRateLimiter;
  }

  AsyncRateLimiter getScreenshotRateLimiter() {
    if (screenshotRateLimiter != null) return screenshotRateLimiter;
    screenshotRateLimiter = new AsyncRateLimiter(SCREENSHOT_FRAMES_PER_SECOND, () -> {
      return updateScreenshot();
    }, this);
    return screenshotRateLimiter;
  }

  public InspectorObjectGroupManager getHovers() {
    if (hover != null && hover.getInspectorService() == getInspectorService()) {
      return hover;
    }
    if (getInspectorService() == null) return null;
    hover = new InspectorObjectGroupManager(getInspectorService(), "hover");
    return hover;
  }

  public @Nullable
  InspectorService.ObjectGroup getScreenshotGroup() {
    if (screenshotGroup != null && screenshotGroup.getInspectorService() == getInspectorService()) {
      return screenshotGroup;
    }
    if (getInspectorService() == null) return null;
    screenshotGroup = getInspectorService().createObjectGroup("screenshot");
    return screenshotGroup;
  }

  protected double getDPI() {
    return JBUI.pixScale(getComponent());
  }

  protected abstract Component getComponent();

  protected int toPixels(int value) {
    return (int)(value * getDPI());
  }

  Rectangle getScreenshotBoundsTight() {
    // TODO(jacobr): cache this.
    if (screenshotBounds == null || extraHeight == 0) return screenshotBounds;
    Rectangle bounds = new Rectangle(screenshotBounds);
    bounds.height -= extraHeight;
    bounds.y += extraHeight;
    return bounds;
  }

  @Override
  public void onSelectionChanged(DiagnosticsNode selection) {
    super.onSelectionChanged(selection);
    if (!visible) {
      return;
    }

    if (getLocation() != null) {
      // The screenshot is dirty because the selection could influence which
      // of the widgets matching the location should be displayed.
      screenshotDirty = true;
    }
    // Fetching a screenshot is somewhat slow so we schedule getting just the
    // bounding boxes before the screenshot arives to show likely correct
    // bounding boxes sooner.
    getGroups().cancelNext();
    InspectorService.ObjectGroup nextGroup = getGroups().getNext();
    final CompletableFuture<ArrayList<DiagnosticsNode>> selectionResults = nextGroup.getBoundingBoxes(getSelectedElement(), selection);

    nextGroup.safeWhenComplete(selectionResults, (boxes, error) -> {
      if (isDisposed) return;
      if (error != null || boxes == null) {
        return;
      }
      // To be paranoid, avoid the inspector going out of scope.
      if (getGroups() == null) return;
      getGroups().promoteNext();
      this.boxes = boxes;
      forceRender();
    });

    // Showing just the selected elements is dangerous as they may be out of
    // sync with the current screenshot if it has changed. To be safe, schedule
    // a screenshot with the updated UI.
    getScreenshotRateLimiter().scheduleRequest();
  }

  public CompletableFuture<?> updateMouse(boolean navigateTo) {
    final InspectorObjectGroupManager hoverGroups = getHovers();
    if (hoverGroups == null || ((popupActive() || popopOpenInProgress) && !navigateTo)) {
      return CompletableFuture.completedFuture(null);
    }
    final Screenshot latestScreenshot = getScreenshotNow();
    if (screenshotBounds == null ||
        latestScreenshot == null ||
        lastPoint == null ||
        !getScreenshotBoundsTight().contains(lastPoint) ||
        getSelectedElement() == null) {
      return CompletableFuture.completedFuture(null);
    }
    hoverGroups.cancelNext();
    final InspectorService.ObjectGroup nextGroup = hoverGroups.getNext();
    final Matrix4 matrix = buildTransformToScreenshot(latestScreenshot);
    matrix.invert();
    final Vector3 point = matrix.perspectiveTransform(new Vector3(lastPoint.getX(), lastPoint.getY(), 0));
    final String file;
    final int startLine, endLine;
    if (controlDown || getVirtualFile() == null) {
      file = null;
    }
    else {
      file = toSourceLocationUri(getVirtualFile().getPath());
    }

    final TextRange activeRange = getActiveRange();
    if (controlDown || activeRange == null || getDocument() == null) {
      startLine = -1;
      endLine = -1;
    }
    else {
      startLine = getDocument().getLineNumber(activeRange.getStartOffset());
      endLine = getDocument().getLineNumber(activeRange.getEndOffset());
    }

    final CompletableFuture<ArrayList<DiagnosticsNode>> hitResults =
      nextGroup.hitTest(getSelectedElement(), point.getX(), point.getY(), file, startLine, endLine);
    nextGroup.safeWhenComplete(hitResults, (hits, error) -> {

      if (isDisposed) return;
      if (error != null || hits == null) {
        return;
      }
      currentHits = hits;
      hoverGroups.promoteNext();
      // TODO(jacobr): consider removing the navigateTo option?
      if (navigateTo && popopOpenInProgress) {
        final DiagnosticsNode node = hits.size() > 0 ? hits.get(0) : null;
        if (node == null) return;
        final TransformedRect transform = node.getTransformToRoot();
        if (transform != null) {
          double x, y;
          final Matrix4 transformMatrix = buildTransformToScreenshot(latestScreenshot);
          transformMatrix.multiply(transform.getTransform());
          final Rectangle2D rect = transform.getRectangle();
          final Vector3 transformed = transformMatrix.perspectiveTransform(new Vector3(new double[]{rect.getCenterX(), rect.getMinY(), 0}));
          final Point pendingPopupOpenLocation = new Point((int)Math.round(transformed.getX()), (int)Math.round(transformed.getY() + 1));
          showPopup(pendingPopupOpenLocation, node);
        }
        popopOpenInProgress = false;
      }
      if (navigateTo && hits.size() > 0) {
        getGroups().getCurrent().setSelection(hits.get(0).getValueRef(), false, false);
      }
      forceRender();
    });
    return hitResults;
  }

  protected abstract VirtualFile getVirtualFile();

  abstract protected Document getDocument();

  protected abstract void showPopup(Point location, DiagnosticsNode node);

  abstract TextRange getActiveRange();

  void setMouseInScreenshot(boolean v) {
    if (_mouseInScreenshot == v) return;
    _mouseInScreenshot = v;
    forceRender();
  }

  public void updateMouseCursor() {
    if (screenshotBounds == null) {
      setMouseInScreenshot(false);
      return;
    }
    if (lastPoint != null && screenshotBounds.contains(lastPoint)) {
      // TODO(jacobr): consider CROSSHAIR_CURSOR instead which gives more of
      //  a pixel selection feel.
      setCustomCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      setMouseInScreenshot(true);
      if (!getScreenshotBoundsTight().contains(lastPoint)) {
        cancelHovers();
      }
    }
    else {
      setCustomCursor(null);
      _mouseOutOfScreenshot();
    }
  }

  abstract void setCustomCursor(@Nullable Cursor cursor);

  void registerLastEvent(MouseEvent event) {
    lastPoint = event.getPoint();
    controlDown = event.isControlDown();
    altDown = event.isAltDown();
    updateMouseCursor();
  }

  public void onMouseMoved(MouseEvent event) {
    assert (!event.isConsumed());
    registerLastEvent(event);
    if (_mouseInScreenshot) {
      event.consume();
      if (getScreenshotBoundsTight().contains(lastPoint) && !popupActive()) {
        getMouseRateLimiter().scheduleRequest();
      }
    }
  }

  boolean popupActive() {
    return popup != null && !popup.isDisposed() || popopOpenInProgress;
  }

  protected void _hidePopup() {
    if (popup != null && !popup.isDisposed()) {
      popup.dispose();
      popup = null;
    }
  }

  public void onMousePressed(MouseEvent event) {
    registerLastEvent(event);
    final Point point = event.getPoint();
    if (screenshotBounds != null && screenshotBounds.contains(point)) {
      event.consume();
      if (isPopupTrigger(event)) {
        _hidePopup();
        popopOpenInProgress = true;
        updateMouse(true);
      }
    }
  }

  private boolean isPopupTrigger(MouseEvent event) {
    return ENABLE_POPUP_SUPPORT && event.isPopupTrigger();
  }

  @Override
  public void onMouseReleased(MouseEvent event) {
    registerLastEvent(event);

    if (screenshotBounds == null) return;
    final Point point = event.getPoint();
    if (screenshotBounds.contains(point)) {
      Rectangle tightBounds = getScreenshotBoundsTight();
      event.consume();
      if (tightBounds.contains(point)) {
        if (isPopupTrigger(event)) {
          _hidePopup();
          popopOpenInProgress = true;
        }
        updateMouse(true);
      }
      else {
        // Title hit.
        _hidePopup();
        if (elements != null) {
          if (elements.size() > 1) {
            int newIndex = this.activeIndex + 1;
            if (newIndex >= elements.size()) {
              newIndex = 1; // Wrap around hack case because the first index is out of order.
            }
            // TODO(jacobr): we could update activeIndex now instead of waitng for the UI to update.
            getGroups().getCurrent().setSelection(elements.get(newIndex).getValueRef(), false, true);
          }
          else if (elements.size() == 1) {
            // Select current.
            getGroups().getCurrent().setSelection(elements.get(0).getValueRef(), false, true);
          }
        }
      }
    }
  }

  public void onMouseExited(MouseEvent event) {
    lastPoint = null;
    controlDown = false;
    altDown = false;
    setMouseInScreenshot(false);
    updateMouseCursor();
  }

  public void onFlutterFrame() {
    fetchScreenshot(false);
  }

  public void _mouseOutOfScreenshot() {
    setMouseInScreenshot(false);
    lastPoint = null;
    cancelHovers();
  }

  public void cancelHovers() {
    hover = getHovers();
    if (hover != null) {
      hover.cancelNext();
      controlDown = false;
      altDown = false;
    }
    if (currentHits != null) {
      currentHits = null;
      forceRender();
    }
  }

  public void onMouseEntered(MouseEvent event) {
    onMouseMoved(event);
  }

  void clearState() {
    screenshotDirty = true;
    currentHits = new ArrayList<>();
    if (hover != null) {
      hover.clear(true);
    }
    screenshot = null;
  }

  @Override
  public void onInspectorAvailabilityChanged() {
    clearState();
  }

  public void onVisibleChanged() {
    if (!visible) {
      _hidePopup();
    }
    if (visible) {
      computeScreenshotBounds();
      if (getInspectorService() != null) {
        if (screenshot == null || screenshotDirty) {
          fetchScreenshot(false);
        }
      }
    }
  }

  abstract public void computeScreenshotBounds();

  @Override
  public boolean updateVisiblityLocked(Rectangle newRectangle) {
    if (popupActive()) {
      lastLockedRectangle = new Rectangle(newRectangle);
      return true;
    }
    if (lastLockedRectangle != null && visible) {
      if (newRectangle.intersects(lastLockedRectangle)) {
        return true;
      }
      // Stop locking.
      lastLockedRectangle = null;
    }
    return false;
  }

  // When visibility is locked, we should try to keep the preview in view even
  // if the user navigates around the code editor.
  public boolean isVisiblityLocked() {
    if (popupActive()) {
      return true;
    }
    if (lastLockedRectangle != null && visible && visibleRect != null) {
      return visibleRect.intersects(lastLockedRectangle);
    }
    return false;
  }

  public Screenshot getScreenshotNow() {
    return screenshot;
  }

  /**
   * Builds a transform that maps global window coordinates to coordinates
   * within the screenshot.
   */
  protected Matrix4 buildTransformToScreenshot(Screenshot latestScreenshot) {
    final Matrix4 matrix = Matrix4.identity();
    matrix.translate(screenshotBounds.x, screenshotBounds.y + extraHeight, 0);
    final Rectangle2D imageRect = latestScreenshot.transformedRect.getRectangle();
    final double centerX = imageRect.getCenterX();
    final double centerY = imageRect.getCenterY();
    matrix.translate(-centerX, -centerY, 0);
    matrix.scale(1 / getDPI(), 1 / getDPI(), 1 / getDPI());
    matrix.translate(centerX * getDPI(), centerY * getDPI(), 0);
    matrix.multiply(latestScreenshot.transformedRect.getTransform());
    return matrix;
  }

  protected ArrayList<DiagnosticsNode> getNodesToHighlight() {
    return currentHits != null && currentHits.size() > 0 ? currentHits : boxes;
  }

  protected void clearScreenshot() {
    if (getScreenshotNow() != null) {
      screenshot = null;
      computeScreenshotBounds();
      forceRender();
    }
  }

  @Override
  public void setElements(ArrayList<DiagnosticsNode> elements) {
    super.setElements(elements);
    currentHits = null;
    boxes = null;
  }

  boolean hasCurrentHits() {
    return currentHits != null && !currentHits.isEmpty();
  }

  public void onMaybeFetchScreenshot() {
    if (screenshot == null || screenshotDirty) {
      fetchScreenshot(false);
    }
  }

  void fetchScreenshot(boolean mightBeIncompatible) {
    if (mightBeIncompatible) {
      screenshotLoading = true;
    }
    screenshotDirty = true;
    if (!visible) return;

    getScreenshotRateLimiter().scheduleRequest();
  }

  protected CompletableFuture<InspectorService.InteractiveScreenshot> updateScreenshot() {
    final InspectorService.ObjectGroup group = getScreenshotGroup();
    if (group == null) {
      return CompletableFuture.completedFuture(null);
    }

    Dimension previewSize = getPreviewSize();
    final long startTime = System.currentTimeMillis();
    // 0.7 is a tweak to ensure we do not try to download enormous screenshots.
    CompletableFuture<InspectorService.InteractiveScreenshot> screenshotFuture =
      group.getScreenshotAtLocation(getLocation(), 10, toPixels(previewSize.width), toPixels(previewSize.height), getDPI() * 0.7);
    group.safeWhenComplete(
      screenshotFuture,
      (pair, e2) -> {
        if (e2 != null || isDisposed || getInspectorService() == null) return;
        if (pair == null) {
          setElements(null);
          screenshot = null;
          boxes = null;
        }
        else {
          setElements(pair.elements);
          screenshot = pair.screenshot;
          boxes = pair.boxes;
        }
        screenshotDirty = false;
        screenshotLoading = false;
        // This calculation might be out of date due to the new screenshot.
        computeScreenshotBounds();
        forceRender();
      }
    );
    return screenshotFuture;
  }

  protected abstract Dimension getPreviewSize();

  public void paint(@NotNull Graphics g, int lineHeight) {
    final Graphics2D g2d = (Graphics2D)g.create();
    // Required to render colors with an alpha channel. Rendering with an
    // alpha chanel makes it easier to keep relationships between shadows
    // and lines looking consistent when the background color changes such
    // as in the case of selection or a different highlighter turning the
    // background yellow.
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1));
    final Rectangle clip = g2d.getClipBounds();
    computeScreenshotBounds();
    if (screenshotBounds == null || !clip.intersects(screenshotBounds)) {
      return;
    }

    final Screenshot latestScreenshot = getScreenshotNow();
    final int imageWidth = screenshotBounds.width;
    final int imageHeight = screenshotBounds.height;

    if (drawBackground) {
      /// XXX hack to draw a shadow.
      final int SHADOW_HEIGHT = 5;
      for (int h = 1; h < SHADOW_HEIGHT; h++) {
        g2d.setColor(new Color(43, 43, 43, 100 - h * 20));
        g2d.fillRect(screenshotBounds.x - h + 1, screenshotBounds.y + min(screenshotBounds.height, imageHeight) + h,
                     min(screenshotBounds.width, imageWidth) + h - 1,
                     1);
        g2d.fillRect(screenshotBounds.x - h + 1,
                     screenshotBounds.y - h,
                     min(screenshotBounds.width, imageWidth) + h - 1,
                     1);
        g2d.fillRect(screenshotBounds.x - h, screenshotBounds.y - h, 1, imageHeight + 2 * h);
      }
    }
    g2d.clip(screenshotBounds);

    final Font font = UIUtil.getFont(UIUtil.FontSize.MINI, UIUtil.getTreeFont());
    g2d.setFont(font);
    // Request a thumbnail and render it in the space available.

    g2d.setColor(isSelected ? JBColor.GRAY : JBColor.LIGHT_GRAY);

    if (latestScreenshot != null) {
      g2d.clip(getScreenshotBoundsTight());

      g2d.drawImage(latestScreenshot.image,
                    new AffineTransform(1 / getDPI(), 0f, 0f, 1 / getDPI(), screenshotBounds.x, screenshotBounds.y + extraHeight), null);

      final java.util.List<DiagnosticsNode> nodesToHighlight = getNodesToHighlight();
      // Sometimes it is fine to display even if we are loading.
      // TODO(jacobr): be smarter and track if the highlights are associated with a different screenshot.
      if (nodesToHighlight != null && nodesToHighlight.size() > 0) { //&& !screenshotLoading) {
        boolean first = true;
        for (DiagnosticsNode box : nodesToHighlight) {
          final TransformedRect transform = box.getTransformToRoot();
          if (transform != null) {
            double x, y;
            final Matrix4 matrix = buildTransformToScreenshot(latestScreenshot);
            matrix.multiply(transform.getTransform());
            final Rectangle2D rect = transform.getRectangle();

            final Vector3[] points = new Vector3[]{
              matrix.perspectiveTransform(new Vector3(new double[]{rect.getMinX(), rect.getMinY(), 0})),
              matrix.perspectiveTransform(new Vector3(new double[]{rect.getMaxX(), rect.getMinY(), 0})),
              matrix.perspectiveTransform(new Vector3(new double[]{rect.getMaxX(), rect.getMaxY(), 0})),
              matrix.perspectiveTransform(new Vector3(new double[]{rect.getMinX(), rect.getMaxY(), 0}))
            };

            final Polygon polygon = new Polygon();
            for (Vector3 point : points) {
              polygon.addPoint((int)Math.round(point.getX()), (int)Math.round(point.getY()));
            }

            if (first && elements.size() > 0 && !Objects.equals(box.getValueRef(), elements.get(0).getValueRef())) {
              g2d.setColor(FlutterEditorColors.HIGHLIGHTED_RENDER_OBJECT_BORDER_COLOR);
              g2d.fillPolygon(polygon);
            }
            g2d.setStroke(FlutterEditorColors.SOLID_STROKE);
            g2d.setColor(FlutterEditorColors.HIGHLIGHTED_RENDER_OBJECT_BORDER_COLOR);
            g2d.drawPolygon(polygon);
          }
          first = false;
        }
      }
    }
    else {
      if (drawBackground) {
        g2d.setColor(isSelected ? JBColor.GRAY : JBColor.LIGHT_GRAY);
        g2d.fillRect(screenshotBounds.x, screenshotBounds.y, screenshotBounds.width, screenshotBounds.height);
        g2d.setColor(FlutterEditorColors.SHADOW_GRAY);
      }
      g2d.setColor(JBColor.BLACK);

      drawMultilineString(g2d, getNoScreenshotMessage(), screenshotBounds.x + 4, screenshotBounds.y + +lineHeight - 4, lineHeight);
    }
    g2d.setClip(clip);

    g2d.dispose();
  }

  String getNoScreenshotMessage() {
    return getInspectorService() == null ? "Run the application to\nactivate device mirror." : "Loading...";
  }

  // TODO(jacobr): perhaps cache and optimize.
  protected void drawMultilineString(Graphics g, String s, int x, int y, int lineHeight) {
    for (String line : s.split("\n")) {
      g.drawString(line, x, y);
      y += lineHeight;
    }
  }
}

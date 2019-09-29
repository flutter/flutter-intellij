/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBScrollBar;
import gnu.trove.THashSet;
import io.flutter.utils.animation.Curve;
import io.flutter.utils.animation.Curves;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.lang.Math.abs;
import static java.lang.Math.max;

/**
 * This class provides animated scrolling of tree views.
 * <p>
 * If autoHorizontalScroll is true, the tree automatically scrolls horizontally
 * to keep as many rows as possible in view any time the tree scrolls vertically.
 *
 * All scrolling operations are animated to improve usability.
 */
public class TreeScrollAnimator implements Disposable {

  private final InspectorTree tree;
  private final JScrollPane scrollPane;
  private final Timer timer;
  private final Timer scrollIdleTimer;
  private Set<TreePath> targets;
  private long animationStartTime;

  private final LockableScrollbar[] scrollbars = {
    new LockableScrollbar(JScrollBar.HORIZONTAL),
    new LockableScrollbar(JScrollBar.VERTICAL)
  };

  /**
   * Last time the user initiated a scroll using a scrollbar.
   */
  private long lastScrollTime;

  /**
   * To avoid bad interactions when autoHorizontalScroll is true, we only
   * allow one scrollbar to be active at a time.
   * <p>
   * Allowed values:
   * * JScrollBar.NO_ORIENTATION neither scrollback currently active. The next
   * active scroll directionwins.
   * * JScrollBar.VERTICAL the vertical scrollbaris active and the horizontal
   * scrollbar is locked.
   * * JScrollBar.HORIZONTAL the horizontal scrollbaris active and the vertical
   * scrollbar is locked.
   * * JScrollBar.ABORT both scrollbar are locked.
   */
  private int activeScrollbar = JScrollBar.NO_ORIENTATION;

  /**
   * Duration of current animation.
   */
  double animationDuration;

  /**
   * Minumum amount to attempt to keep the left side of the tree indented by.
   */
  static final double TARGET_LEFT_INDENT = 25.0;
  /**
   * Min delay before changes from switching horizontally to vertically and
   * vice versa are allowed. This is to avoid accidental undesirable mouse
   * wheel based scrolls.
   */
  static final int MS_DELAY_BEFORE_CHANGING_SCROLL_AXIS = 150;
  static final int DEFAULT_ANIMATION_DURATION = 150;
  /**
   * Animation duration if we are only changing the X axis and not the Y axis.
   */
  static final int DEFAULT_ANIMATE_X_DURATION = 80;

  private Point animationStart;
  private Point animationEnd;

  private Curve animationCurve;

  private boolean scrollTriggeredAnimator = false;

  /**
   * Last observed scroll position of the scrollPane.
   */
  private Point scrollPosition;
  private boolean autoHorizontalScroll;

  /**
   * Scrollbar that can be locked to prevent accidental user scrolls triggered
   * by the touchpad.
   * <p>
   * The purpose of this class is to improve behavior handling mouse wheel
   * scroll triggered by a trackpad where it can be easy to accidentally
   * trigger both horizontal and vertical scroll. This class can be used to
   * help avoid spurious scroll in that case. Unfortunately, the UI is redrawn
   * for one frame before we would have a chance to reset the scroll position
   * so we have to solve locking scroll this way instead of by resetting
   * spurious scrolls after we get an onScroll event.
   * Using JScrollPane.setHorizontalScrollBarPolicy cannot achieve the desired
   * effect because toggling that option on and off results in Swing scrolling
   * the UI to try to be clever about which UI is in view.
   */
  private class LockableScrollbar extends JBScrollBar {
    boolean allowScroll;

    LockableScrollbar(int orientation) {
      super(orientation);
      allowScroll = true;
    }

    void setAllowScroll(boolean value) {
      allowScroll = value;
    }

    @Override
    public boolean isVisible() {
      return allowScroll && super.isVisible();
    }
  }

  public TreeScrollAnimator(InspectorTree tree, JScrollPane scrollPane) {
    this.tree = tree;
    this.scrollPane = scrollPane;
    // Our target of 60fps  is perhaps a bit ambitious given the rendering
    // pipeline used by IntelliJ.
    timer = new Timer(1000 / 60, this::onFrame);
    scrollIdleTimer = new Timer(MS_DELAY_BEFORE_CHANGING_SCROLL_AXIS, this::onScrollIdle);
    scrollPane.setHorizontalScrollBar(scrollbars[JScrollBar.HORIZONTAL]);
    scrollPane.setVerticalScrollBar(scrollbars[JScrollBar.VERTICAL]);

    // TODO(jacobr): is this useful?
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setAutoscrolls(false);
    tree.setScrollsOnExpand(false);

    scrollPane.getVerticalScrollBar().getModel().addChangeListener(this::verticalScrollChanged);
    scrollPane.getHorizontalScrollBar().getModel().addChangeListener(this::horizontalScrollChanged);
    scrollPosition = scrollPane.getViewport().getViewPosition();
    computeScrollPosition();
  }

  public void setAutoHorizontalScroll(boolean autoHorizontalScroll) {
    this.autoHorizontalScroll = autoHorizontalScroll;
    if (autoHorizontalScroll) {
      applyAutoHorizontalScroll();
    }
    else if (!timer.isRunning()) {
      setActiveScrollbar(JScrollBar.NO_ORIENTATION);
    }
  }

  private void computeScrollPosition() {
    scrollPosition =
      new Point(scrollbars[JScrollBar.HORIZONTAL].getModel().getValue(), scrollbars[JScrollBar.VERTICAL].getModel().getValue());
  }

  private void handleScrollChanged() {
    final Point last = scrollPosition;
    computeScrollPosition();

    if (!autoHorizontalScroll) {
      return;
    }

    final int dx = scrollPosition.x - last.x;
    final int dy = scrollPosition.y - last.y;
    if (dx == 0 && dy == 0) {
      return;
    }
    if (scrollTriggeredAnimator || timer.isRunning()) {
      return;
    }

    final int orientation = abs(dy) >= abs(dx) ? JScrollBar.VERTICAL : JScrollBar.HORIZONTAL;

    if (activeScrollbar != JScrollBar.NO_ORIENTATION && activeScrollbar != orientation) {
      // This should only occur if  vertical and horizontal scrolling was initiated at the same time.
      return;
    }

    lastScrollTime = System.currentTimeMillis();
    setActiveScrollbar(orientation);
    scrollIdleTimer.restart();
  }

  private void setActiveScrollbar(int orientation) {
    if (activeScrollbar != orientation) {
      activeScrollbar = orientation;
      if (orientation == JScrollBar.ABORT) {
        for (int axis = 0; axis <= 1; ++axis) {
          scrollbars[axis].setAllowScroll(false);
        }
      }
      else {
        for (int axis = 0; axis <= 1; ++axis) {
          final int otherAxis = 1 - axis;
          scrollbars[axis].setAllowScroll(orientation != otherAxis);
        }
      }
    }
  }

  private void horizontalScrollChanged(ChangeEvent e) {
    handleScrollChanged();
  }

  private void verticalScrollChanged(ChangeEvent e) {
    handleScrollChanged();
    if (targets != null || scrollTriggeredAnimator) {
      return;
    }
    if (autoHorizontalScroll) {
      applyAutoHorizontalScroll();
    }
  }

  private void applyAutoHorizontalScroll() {
    animateToX(calculateTargetX(scrollPosition, null));
  }

  private int calculateTargetX(Point candidate, TreePath selectionPath) {
    final int rowStart = tree.getClosestRowForLocation(candidate.x, candidate.y);
    final int rowEnd = tree.getClosestRowForLocation(candidate.x, candidate.y + scrollPane.getHeight() - 1);
    Rectangle union = null;
    int selectedRow = -1;
    if (selectionPath != null) {
      selectedRow = tree.getRowForPath(selectionPath);
    }
    else {
      final int[] rows = tree.getSelectionRows();
      selectedRow = rows != null && rows.length > 0 ? rows[0] : selectedRow;
    }

    Rectangle selectedBounds = null;
    for (int i = rowStart; i <= rowEnd; ++i) {
      final Rectangle bounds = tree.getRowBounds(i);
      if (i == selectedRow) {
        selectedBounds = bounds;
      }
      union = union == null ? bounds : union.union(bounds);
    }
    if (union == null) {
      // No rows in view.
      return 0;
    }
    int targetX = Math.max(union.x - (int)TARGET_LEFT_INDENT, 0);
    if (selectedBounds != null) {
      // Using the actual selection width which depends on the contents of the nodes
      // results in jumpy and distracting UI so we use a fake selection width
      // instead so scrolling behavior is more stable.
      final int selectionWidth = Math.min(scrollPane.getViewport().getWidth() / 2, 100);
      final Interval xAxis = clampInterval(
        new Interval(selectedBounds.x, selectionWidth),
        new Interval(targetX, selectedBounds.x + selectionWidth - targetX),
        scrollPane.getViewport().getWidth());
      targetX = xAxis.start;
    }
    return targetX;
  }

  private void animateToX(int x) {
    targets = null;
    computeScrollPosition();
    animationStart = scrollPosition;
    setActiveScrollbar(JScrollBar.VERTICAL);

    animationEnd = new Point(x, animationStart.y);
    final long currentTime = System.currentTimeMillis();

    if (!timer.isRunning()) {
      animationCurve = Curves.LINEAR;
      animationDuration = DEFAULT_ANIMATE_X_DURATION;
    }
    else {
      // We have the same target but that target's position has changed.
      // Adjust the animation duration to account for the time we have left
      // ensuring the animation proceeds for at least half the default animation
      // duration.
      animationDuration = Math.max(DEFAULT_ANIMATE_X_DURATION / 2.0, animationDuration - (currentTime - animationStartTime));
      // Ideally we would manage the velocity keeping it consistent
      // with the existing velocity at the animationStart of the animation
      // but this is good enough. We use EASE_OUT assuming the
      // animation was already at a moderate speed when the
      // destination position was updated.

      animationCurve = Curves.LINEAR;
    }
    animationStartTime = currentTime;

    if (!timer.isRunning()) {
      timer.start();
    }
  }

  public static class Interval {
    Interval(int start, int length) {
      this.start = start;
      this.length = length;
    }

    final int start;
    final int length;

    @Override
    public String toString() {
      return "Interval(" + start + ", " + length + ")";
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Interval)) {
        return false;
      }
      final Interval interval = (Interval)o;
      return interval.start == start && interval.length == length;
    }
  }

  /**
   * Determines the best interval that does not exceed clampLength
   * and includes the required interval and as much content from
   * the ideal interval as possible. The clamped interval is expanded
   * proportionally in both directions to reach the size of the ideal
   * interval.
   * <p>
   * The required interval must be inside the ideal interval.
   */
  static Interval clampInterval(Interval required, Interval ideal, int clampLength) {
    if (clampLength < 0) {
      clampLength = 0;
    }
    if (required.start < ideal.start ||
        required.start + required.length > ideal.start + ideal.length ||
        required.length < 0 ||
        ideal.length < 0) {
      // The required bounds must not be outside the ideal bounds.
      throw new IllegalArgumentException();
    }

    if (ideal.length <= clampLength) {
      return ideal;
    }

    final double extraSpace = clampLength - required.length;

    if (extraSpace <= 0) {
      // Required bounds don't even fully fit. Ensure we include the start
      // coordinate in the required bounds.
      return new Interval(required.start, clampLength);
    }

    // Primary bounds fit. Expand the bounds proportionally both before and after the primaryBounds;
    final double desiredSpace = ideal.length - required.length;
    return new Interval(Curves.LINEAR.interpolate(required.start, ideal.start, extraSpace / desiredSpace), clampLength);
  }

  public void animateTo(Rectangle rect) {
    final int firstRow = tree.getClosestRowForLocation(rect.x, rect.y);
    final int lastRow = tree.getClosestRowForLocation(rect.x, rect.y + rect.height);
    final List<TreePath> targets = new ArrayList<>();
    final int[] selectionRows = tree.getSelectionRows();

    final int selectedRow = selectionRows != null && selectionRows.length > 0 ? selectionRows[0] : -1;
    if (selectedRow > firstRow && selectedRow <= lastRow) {
      // Add the selected row first so it is the priority to include
      targets.add(tree.getPathForRow(selectedRow));
    }
    for (int row = firstRow; row <= lastRow; ++row) {
      if (row != selectedRow) {
        targets.add(tree.getPathForRow(row));
      }
    }
    animateTo(targets);
  }

  public void animateTo(List<TreePath> targets) {
    if (targets == null || targets.isEmpty()) {
      return;
    }
    Rectangle bounds = tree.getPathBounds(targets.get(0));
    if (bounds == null) {
      // The target is the child of a collapsed node.
      return;
    }
    final Rectangle primaryBounds = bounds;

    boolean newTarget = true;
    if (this.targets != null) {
      for (TreePath target : targets) {
        if (this.targets.contains(target)) {
          newTarget = false;
        }
      }
    }
    animationStart = scrollPane.getViewport().getViewPosition();
    // Grow bound up to half the width of the window to the left so that
    // connections to ancestors are still visible. Otherwise, the window could
    // get scrolled so that ancestors are all hidden with the new target placed
    // on the left side of the window.
    final double minX = max(0.0, bounds.getMinX() - Math.min(scrollPane.getWidth() * 0.5, TARGET_LEFT_INDENT));
    final double maxX = bounds.getMaxX();
    final double y = bounds.getMinY();
    final double height = bounds.getHeight();
    bounds.setRect(minX, y, maxX - minX, height);
    // Add secondary targets to the bounding rectangle.
    for (int i = 1; i < targets.size(); i++) {
      final Rectangle secoundaryBounds = tree.getPathBounds(targets.get(i));
      if (secoundaryBounds != null) {
        bounds = bounds.union(secoundaryBounds);
      }
    }

    // We need to clarify that we care most about keeping the top left corner
    // of the primary bounds in view by clamping if our bounds are larger than the viewport.
    final Interval xAxis = clampInterval(
      new Interval(primaryBounds.x, primaryBounds.width),
      new Interval(bounds.x, bounds.width),
      scrollPane.getViewport().getWidth());
    final Interval yAxis = clampInterval(
      new Interval(primaryBounds.y, primaryBounds.height),
      new Interval(bounds.y, bounds.height),
      scrollPane.getViewport().getHeight());
    bounds.setBounds(xAxis.start, yAxis.start, xAxis.length, yAxis.length);
    scrollTriggeredAnimator = true;
    if (timer.isRunning()) {
      // Compute where to scroll to show the target bounds from the location
      // the currend animation ends at.
      scrollPane.getViewport().setViewPosition(animationEnd);
    }
    tree.immediateScrollRectToVisible(bounds);
    animationEnd = scrollPane.getViewport().getViewPosition();
    if (autoHorizontalScroll) {
      // Post process the position so we are 100% consistent with the algorithm
      // used for automatic horizontal scroll.
      int targetX = calculateTargetX(animationEnd, targets.get(0));
      animationEnd = new Point(targetX, animationEnd.y);
    }

    scrollPane.getViewport().setViewPosition(animationStart);
    scrollTriggeredAnimator = false;
    if (animationStart.y == animationEnd.y && animationStart.x == animationEnd.x) {
      // No animation required.
      return;
    }

    this.targets = new THashSet<>(targets);

    final long currentTime = System.currentTimeMillis();

    if (newTarget) {
      animationCurve = Curves.EASE_IN_OUT;
      animationDuration = DEFAULT_ANIMATION_DURATION;
    }
    else {
      // We have the same target but that target's position has changed.
      // Adjust the animation duration to account for the time we have left
      // ensuring the animation proceeds for at least half the default animation
      // duration.
      animationDuration = Math.max(DEFAULT_ANIMATION_DURATION / 2.0, animationDuration - (currentTime - animationStartTime));
      // Ideally we would manage the velocity keeping it consistent
      // with the existing velocity at the animationStart of the animation
      // but this is good enough. We use EASE_OUT assuming the
      // animation was already at a moderate speed when the
      // destination position was updated.

      animationCurve = Curves.EASE_OUT;
    }
    animationStartTime = currentTime;

    setActiveScrollbar(JScrollBar.ABORT);
    if (!timer.isRunning()) {
      timer.start();
    }
  }

  private void setScrollPosition(int x, int y) {
    scrollPosition = new Point(x, y);
    scrollTriggeredAnimator = true;
    scrollPane.getViewport().setViewPosition(scrollPosition);
    scrollTriggeredAnimator = false;
  }

  private void onFrame(ActionEvent e) {
    final long now = System.currentTimeMillis();
    final long delta = now - animationStartTime;
    final double fraction = Math.min((double)delta / animationDuration, 1.0);
    final boolean animateX = animationStart.x != animationEnd.x;
    final boolean animateY = animationStart.y != animationEnd.y;
    final Point current = scrollPane.getViewport().getViewPosition();
    final int x = animateX ? animationCurve.interpolate(animationStart.x, animationEnd.x, fraction) : current.x;
    final int y = animateY ? animationCurve.interpolate(animationStart.y, animationEnd.y, fraction) : current.y;
    setScrollPosition(x, y);
    if (fraction >= 1.0) {
      targets = null;
      setActiveScrollbar(JScrollBar.NO_ORIENTATION);
      timer.stop();
    }
  }

  private void onScrollIdle(ActionEvent e) {
    if (activeScrollbar != JScrollBar.ABORT && !timer.isRunning()) {
      setActiveScrollbar(JScrollBar.NO_ORIENTATION);
    }
  }

  @Override
  public void dispose() {
    if (timer.isRunning()) {
      timer.stop();
    }
    if (scrollIdleTimer.isRunning()) {
      scrollIdleTimer.stop();
    }
  }
}

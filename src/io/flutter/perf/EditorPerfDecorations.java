/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.xdebugger.XSourcePosition;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncUtils;
import io.flutter.view.FlutterPerfView;
import io.flutter.view.InspectorPerfTab;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is a view model managing display of performance statistics for
 * a specific TextEditor using RangeHighlighters to show the performance
 * statistics as animated icons in the gutter of the text editor and by
 * highlighting the ranges of text corresponding to the performance statistcs.
 */
class EditorPerfDecorations implements EditorMouseListener, EditorPerfModel {
  private static final int HIGHLIGHTER_LAYER = HighlighterLayer.SELECTION - 1;

  /**
   * Experimental option to animate highlighted widget names.
   * <p>
   * Disabled by default as animating contents of the TextEditor results in
   * higher than desired memory usage.
   */
  public static boolean ANIMATE_WIDGET_NAME_HIGLIGHTS = false;

  @NotNull
  private final TextEditor textEditor;
  @NotNull
  private final FlutterApp app;
  @NotNull
  private FilePerfInfo stats;

  private boolean hasDecorations = false;
  private boolean hoveredOverLineMarkerArea = false;

  private final Map<TextRange, PerfGutterIconRenderer> perfMarkers = new HashMap<>();
  private boolean alwaysShowLineMarkersOverride = false;

  EditorPerfDecorations(@NotNull TextEditor textEditor, @NotNull FlutterApp app) {
    this.textEditor = textEditor;
    this.app = app;
    stats = new FilePerfInfo();
    textEditor.getEditor().addEditorMouseListener(this);
  }

  @Override
  public boolean getAlwaysShowLineMarkers() {
    return hoveredOverLineMarkerArea || alwaysShowLineMarkersOverride;
  }

  @Override
  public void setAlwaysShowLineMarkersOverride(boolean show) {
    boolean lastValue = getAlwaysShowLineMarkers();
    alwaysShowLineMarkersOverride = show;
    if (lastValue != getAlwaysShowLineMarkers()) {
      updateIconUIAnimations();
    }
  }

  @NotNull
  @Override
  public FilePerfInfo getStats() {
    return stats;
  }

  @NotNull
  @Override
  public TextEditor getTextEditor() {
    return textEditor;
  }

  @NotNull
  @Override
  public FlutterApp getApp() {
    return app;
  }

  void setHasDecorations(boolean value) {
    if (value != hasDecorations) {
      hasDecorations = value;
    }
  }

  @Override
  public void setPerfInfo(FilePerfInfo stats) {
    this.stats = stats;
    final Editor editor = textEditor.getEditor();
    final MarkupModel markupModel = editor.getMarkupModel();

    // Remove markers that aren't in the new perf report.
    final List<TextRange> rangesToRemove = new ArrayList<>();
    for (TextRange range : perfMarkers.keySet()) {
      if (!stats.hasLocation(range)) {
        rangesToRemove.add(range);
      }
    }
    for (TextRange range : rangesToRemove) {
      removeMarker(range);
    }

    for (TextRange range : stats.getLocations()) {
      final PerfGutterIconRenderer existing = perfMarkers.get(range);
      if (existing == null) {
        addRangeHighlighter(range, markupModel);
      }
      else {
        existing.updateUI(true);
      }
    }
    setHasDecorations(true);
  }

  private void removeMarker(TextRange range) {
    final PerfGutterIconRenderer marker = perfMarkers.remove(range);
    if (marker != null) {
      final Editor editor = textEditor.getEditor();
      final MarkupModel markupModel = editor.getMarkupModel();
      markupModel.removeHighlighter(marker.getHighlighter());
    }
  }

  @Override
  public void markAppIdle() {
    stats.markAppIdle();
    updateIconUIAnimations();
  }

  private void addRangeHighlighter(TextRange textRange, MarkupModel markupModel) {
    final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
      textRange.getStartOffset(), textRange.getEndOffset(), HIGHLIGHTER_LAYER, new TextAttributes(), HighlighterTargetArea.EXACT_RANGE);

    final PerfGutterIconRenderer renderer = new PerfGutterIconRenderer(
      textRange,
      this,
      rangeHighlighter
    );
    rangeHighlighter.setGutterIconRenderer(renderer);
    assert !perfMarkers.containsKey(textRange);
    perfMarkers.put(textRange, renderer);
  }

  @Override
  public boolean isAnimationActive() {
    return getStats().getTotalValue(PerfMetric.peakRecent) > 0;
  }

  @Override
  public void onFrame() {
    if (app.isReloading() || !hasDecorations || !isAnimationActive()) {
      return;
    }
    updateIconUIAnimations();
  }

  private void updateIconUIAnimations() {
    if (!textEditor.getComponent().isVisible()) {
      return;
    }
    for (PerfGutterIconRenderer marker : perfMarkers.values()) {
      marker.updateUI(true);
    }
  }

  private void removeHighlightersFromEditor() {
    final List<RangeHighlighter> highlighters = new ArrayList<>();
    final MarkupModel markupModel = textEditor.getEditor().getMarkupModel();

    for (PerfGutterIconRenderer marker : perfMarkers.values()) {
      markupModel.removeHighlighter(marker.getHighlighter());
    }
    perfMarkers.clear();
    setHasDecorations(false);
  }

  public void flushDecorations() {
    if (hasDecorations && textEditor.isValid()) {
      setHasDecorations(false);
      ApplicationManager.getApplication().invokeLater(this::removeHighlightersFromEditor);
    }
  }

  @Override
  public void dispose() {
    textEditor.getEditor().removeEditorMouseListener(this);
    flushDecorations();
  }

  @Override
  public void mousePressed(EditorMouseEvent e) {
  }

  @Override
  public void mouseClicked(EditorMouseEvent e) {
  }

  @Override
  public void mouseReleased(EditorMouseEvent e) {
  }

  @Override
  public void mouseEntered(EditorMouseEvent e) {
    final EditorMouseEventArea area = e.getArea();
    if (!hoveredOverLineMarkerArea &&
        area == EditorMouseEventArea.LINE_MARKERS_AREA ||
        area == EditorMouseEventArea.FOLDING_OUTLINE_AREA ||
        area == EditorMouseEventArea.LINE_NUMBERS_AREA) {
      // Hover is over the gutter area.
      setHoverState(true);
    }
  }

  @Override
  public void mouseExited(EditorMouseEvent e) {
    final EditorMouseEventArea area = e.getArea();
    setHoverState(false);
    // TODO(jacobr): hovers over a tooltip triggered by a gutter icon should
    // be considered a hover of the gutter but this logic does not handle that
    // case correctly.
  }

  private void setHoverState(boolean value) {
    if (value != hoveredOverLineMarkerArea) {
      hoveredOverLineMarkerArea = value;
      updateIconUIAnimations();
    }
  }

  @Override
  public void clear() {
    stats.clear();
    removeHighlightersFromEditor();
  }
}

/**
 * This class renders the animated gutter icons used to visualize how much
 * widget repaint or rebuild work is happening.
 * <p>
 * This is a somewhat strange GutterIconRender in that we use it to orchestrate
 * animating the color of the associated RangeHighlighter and changing the icon
 * of the GutterIconRenderer when performance changes without requiring the
 * GutterIconRenderer to be discarded. markupModel.fireAttributesChanged is
 * used to notify the MarkupModel when state has changed and a rerender is
 * required.
 */
class PerfGutterIconRenderer extends GutterIconRenderer {

  // Speed of the animation in radians per second.
  private static final double ANIMATION_SPEED = 4.0;

  private final RangeHighlighter highlighter;
  private final TextRange range;
  private final EditorPerfModel perfModelForFile;

  // Tracked so we know when to notify that our icon has changed.
  private Icon lastIcon;

  PerfGutterIconRenderer(TextRange range,
                         EditorPerfModel perfModelForFile,
                         RangeHighlighter highlighter) {
    this.highlighter = highlighter;
    this.range = range;
    this.perfModelForFile = perfModelForFile;
    final TextAttributes textAttributes = highlighter.getTextAttributes();
    assert textAttributes != null;
    textAttributes.setEffectType(EffectType.LINE_UNDERSCORE);

    updateUI(false);
  }

  public boolean isNavigateAction() {
    return isActive();
  }

  private FlutterApp getApp() {
    return perfModelForFile.getApp();
  }

  private int getCurrentValue() {
    return perfModelForFile.getStats().getCurrentValue(range);
  }

  private int getDisplayValue() {
    int value = getCurrentValue();
    if (value == 0 && perfModelForFile.getAlwaysShowLineMarkers()) {
      // This is the case where the value was previously non-zero but the app
      // is idle so the value was reset. For all ui rendering logic we treat
      // the value as 1 so that consistent coloring is used throughout the
      // ui. Alternately we could use the original non-zero value but that
      // could be more confusing to users.
      return 1;
    }
    return value;
  }

  private boolean isActive() {
    return getDisplayValue() > 0;
  }

  RangeHighlighter getHighlighter() {
    return highlighter;
  }

  /**
   * Returns the action executed when the icon is left-clicked.
   *
   * @return the action instance, or null if no action is required.
   */
  @Nullable
  public AnAction getClickAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        if (isActive()) {

          final ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(getApp().getProject());
          final ToolWindow flutterPerfToolWindow = toolWindowManager.getToolWindow(FlutterPerfView.TOOL_WINDOW_ID);
          if (flutterPerfToolWindow.isVisible()) {
            showPerfViewMessage();
            return;
          }
          flutterPerfToolWindow.show(() -> showPerfViewMessage());
        }
      }
    };
  }

  private void showPerfViewMessage() {
    final FlutterPerfView flutterPerfView = ServiceManager.getService(getApp().getProject(), FlutterPerfView.class);
    final InspectorPerfTab inspectorPerfTab = flutterPerfView.showPerfTab(getApp());
    String message = "<html><body>" +
                     getTooltipHtmlFragment() +
                     "</body></html>";
    final Iterable<SummaryStats> current = perfModelForFile.getStats().getRangeStats(range);
    if (current.iterator().hasNext()) {
      final SummaryStats first = current.iterator().next();
      final XSourcePosition position = first.getLocation().getXSourcePosition();
      if (position != null) {
        AsyncUtils.invokeLater(() -> {
          position.createNavigatable(getApp().getProject()).navigate(true);
          HintManager.getInstance().showInformationHint(perfModelForFile.getTextEditor().getEditor(), message);
        });
      }
    }
  }

  @NotNull
  @Override
  public Alignment getAlignment() {
    return Alignment.LEFT;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    lastIcon = getIconInternal();
    return lastIcon;
  }

  public Icon getIconInternal() {
    return Icons.getIconForCount(getCurrentValue(), perfModelForFile.getAlwaysShowLineMarkers());
  }

  Color getErrorStripeMarkColor() {
    // TODO(jacobr): tween from green or blue to red depending on the count.
    final int count = getDisplayValue();
    if (count == 0) {
      return null;
    }
    if (count >= Icons.HIGH_LOAD_THRESHOLD) {
      return JBColor.YELLOW;
    }
    return JBColor.GRAY;
  }

  public void updateUI(boolean repaint) {
    final int count = getDisplayValue();
    final TextAttributes textAttributes = highlighter.getTextAttributes();
    assert textAttributes != null;
    boolean changed = false;
    if (count > 0) {
      Color targetColor = getErrorStripeMarkColor();
      if (EditorPerfDecorations.ANIMATE_WIDGET_NAME_HIGLIGHTS) {
        final double animateTime = (double)(System.currentTimeMillis()) * 0.001;
        // TODO(jacobr): consider tracking a start time for the individual
        // animation instead of having all animations running in sync.
        // 1.0 - Math.cos is used so that balance is 0.0 at the start of the animation
        // and the value will vary from 0 to 1.0
        final double balance = (1.0 - Math.cos(animateTime * ANIMATION_SPEED)) * 0.5;
        targetColor = ColorUtil.mix(JBColor.WHITE, targetColor, balance);
      }
      if (!targetColor.equals(textAttributes.getEffectColor())) {
        textAttributes.setEffectColor(targetColor);
        changed = true;
      }
    }
    else {
      textAttributes.setEffectColor(null);
    }
    final Color errorStripeColor = getErrorStripeMarkColor();
    highlighter.setErrorStripeMarkColor(errorStripeColor);
    if (repaint && lastIcon != getIconInternal()) {
      changed = true;
    }
    if (changed && repaint) {
      final MarkupModel markupModel = perfModelForFile.getTextEditor().getEditor().getMarkupModel();
      ((MarkupModelEx)markupModel).fireAttributesChanged((RangeHighlighterEx)highlighter, true, false);
    }
  }

  String getTooltipHtmlFragment() {
    final StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (SummaryStats stats : perfModelForFile.getStats().getRangeStats(range)) {
      final String style = first ? "" : "margin-top: 8px";
      first = false;
      sb.append("<p style='" + style + "'>");
      if (stats.getKind() == PerfReportKind.rebuild) {
        sb.append("Rebuild");
      }
      else if (stats.getKind() == PerfReportKind.repaint) {
        sb.append("Repaint");
      }
      sb.append(" counts for: <strong>" + stats.getDescription());
      sb.append("</strong></p>");
      sb.append("<p style='padding-left: 8px'>");
      sb.append("For last frame: " + stats.getValue(PerfMetric.lastFrame) + "<br>");
      sb.append("In past second: " + stats.getValue(PerfMetric.pastSecond) + "<br>");
      sb.append("Since entering the current screen: " + stats.getValue(PerfMetric.totalSinceEnteringCurrentScreen) + "<br>");
      sb.append("Since last hot reload/restart: " + stats.getValue(PerfMetric.total));
      sb.append("</p>");
    }
    if (sb.length() == 0) {
      sb.append("<p><b>No widget rebuilds detected for line.</b></p>");
    }
    return sb.toString();
  }

  @Override
  public String getTooltipText() {
    return "<html><body>" + getTooltipHtmlFragment() + "</body></html>";
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PerfGutterIconRenderer)) {
      return false;
    }
    final PerfGutterIconRenderer other = (PerfGutterIconRenderer)obj;
    return other.getCurrentValue() == getCurrentValue();
  }

  @Override
  public int hashCode() {
    return getCurrentValue();
  }
}

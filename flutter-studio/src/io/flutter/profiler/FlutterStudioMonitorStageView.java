/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.*;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;

/**
 * Bird eye view displaying high-level information across all profilers.
 * Refactored from Android Studio 3.2 adt-ui code.
 */
public class FlutterStudioMonitorStageView extends FlutterStageView<FlutterStudioMonitorStage> {
  static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);

  public static final Border MONITOR_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, ProfilerColors.MONITOR_BORDER);

  private static final int AXIS_SIZE = 100;

  private List<RangedContinuousSeries> rangedData;
  private LegendComponentModel legendComponentModel;
  private LegendComponent legendComponent;
  private Range timeGlobalRangeUs;

  // TODO(terry): Need to handle when we have more than one view.
  //@NotNull
  //@SuppressWarnings("FieldCanBeLocal") // We need to keep a reference to the sub-views. If they got collected, they'd stop updating the UI.
  //private final List<FlutterProfilerMonitorView> views;

  public FlutterStudioMonitorStageView(@NotNull FlutterStudioProfilersView profilersView,
                                       @NotNull FlutterStudioMonitorStage stage) {
    super(profilersView, stage);

    buildUI(stage);
  }

  private void buildUI(@NotNull FlutterStudioMonitorStage stage) {
    ProfilerTimeline timeline = stage.getStudioProfilers().getTimeline();
    Range viewRange = getTimeline().getViewRange();

    SelectionModel selectionModel = new SelectionModel(timeline.getSelectionRange());
    SelectionComponent selection = new SelectionComponent(selectionModel, timeline.getViewRange());
    selection.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    selectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        // TODO(terry): Bring up the memory object list view using getTimeline().getSelectionRange().getMin() .. getMax().
      }

      @Override
      public void selectionCleared() {
        // Clear stuff here
      }
    });

    RangeTooltipComponent
      tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(),
                                          timeline.getDataRange(), getTooltipPanel(),
                                          getProfilersView().getComponent(), () -> true);

    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    ProfilerScrollbar sb = new ProfilerScrollbar(timeline, panel);
    panel.add(sb, new TabularLayout.Constraint(3, 0));

    FlutterStudioProfilers profilers = stage.getStudioProfilers();
    JComponent timeAxis = buildTimeAxis(profilers);

    panel.add(timeAxis, new TabularLayout.Constraint(2, 0));

    final String HEAP_LABEL = "Heap";

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel("Memory");
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(JLabel.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);

    // TODO(terry): Build the Y-axis size of heap in bytes.
    Range yRange1Animatable = new Range(0, 100);   // TODO(terry): need to make 100 dynamic max of allocated high.
    AxisComponent yAxisBytes;

    ResizingAxisComponentModel yAxisAxisBytesModel =
      new ResizingAxisComponentModel.Builder(yRange1Animatable, MemoryAxisFormatter.DEFAULT).setLabel(HEAP_LABEL).build();
    yAxisBytes = new AxisComponent(yAxisAxisBytesModel, AxisComponent.AxisOrientation.RIGHT);

    yAxisBytes.setShowMax(true);
    yAxisBytes.setShowUnitAtMax(true);
    yAxisBytes.setHideTickAtMin(true);
    yAxisBytes.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    yAxisBytes.setMargins(0, Y_AXIS_TOP_MARGIN);

    LineChartModel model = new LineChartModel();

    FlutterAllMemoryData.ThreadSafeData memoryUsedDataSeries = stage.getMemoryUsedDataSeries();
    FlutterAllMemoryData.ThreadSafeData memoryMaxDataSeries = stage.getMemoryMaxDataSeries();

    RangedContinuousSeries usedMemoryRange =
      new RangedContinuousSeries("Memory", getTimeline().getViewRange(),
                                 new Range(0, 1024 * 1024 * 100), memoryUsedDataSeries);
    RangedContinuousSeries maxMemoryRange =
      new RangedContinuousSeries("MemoryMax", getTimeline().getViewRange(),
                                 new Range(0, 1024 * 1024 * 100), memoryMaxDataSeries);

    model.add(usedMemoryRange);   // Plot used memory
    model.add(maxMemoryRange);    // Plot total size of allocated heap.

    getStage().getStudioProfilers().getUpdater().register(model);
    LineChart mLineChart = new LineChart(model);
    mLineChart.setBackground(JBColor.background());

    // Used Heap is stacked plot.
    mLineChart.configure(usedMemoryRange, new LineConfig(new JBColor(new Color(0x56BFEC),
                                                                     new Color(0x2B7DA2)))
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    mLineChart.configure(maxMemoryRange, new LineConfig(new JBColor(new Color(0x1B4D65),
                                                                    new Color(0xF6F6F6)))
      .setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    mLineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    mLineChart.setTopPadding(Y_AXIS_TOP_MARGIN);
    mLineChart.setFillEndGap(true);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    axisPanel.add(yAxisBytes, BorderLayout.WEST);

    // Build the legend.
    FlutterAllMemoryData.ThreadSafeData memoryMax = stage.getMemoryMaxDataSeries();
    FlutterAllMemoryData.ThreadSafeData memoryUsed = stage.getMemoryUsedDataSeries();

    Range allData = getTimeline().getDataRange();

    legendComponentModel = new LegendComponentModel(new Range(100.0, 100.0));
    timeGlobalRangeUs = new Range(0, 0);

    RangedContinuousSeries maxHeapRangedData = new RangedContinuousSeries("Max Heap", timeGlobalRangeUs, allData, memoryMax);
    RangedContinuousSeries usedHeapRangedData = new RangedContinuousSeries("Heap Used", timeGlobalRangeUs, allData, memoryUsed);

    SeriesLegend legendMax = new SeriesLegend(maxHeapRangedData, MEMORY_AXIS_FORMATTER, timeGlobalRangeUs);
    legendComponentModel.add(legendMax);
    SeriesLegend legendUsed = new SeriesLegend(usedHeapRangedData, MEMORY_AXIS_FORMATTER, timeGlobalRangeUs);
    legendComponentModel.add(legendUsed);

    legendComponent = new LegendComponent(legendComponentModel);

    legendComponent.configure(legendMax, new LegendConfig(LegendConfig.IconType.DASHED_LINE, new Color(0x1B4D65)));
    legendComponent.configure(legendUsed, new LegendConfig(LegendConfig.IconType.BOX, new Color(0x56BFEC)));

    // Place legend in a panel.
    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(legendComponent, BorderLayout.EAST);

    // Make the legend visible.
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));


    monitorPanel.add(tooltip, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(yAxisBytes, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(mLineChart, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(1, "*"); // Give monitor as much space as possible
    panel.add(monitorPanel, new TabularLayout.Constraint(1, 0));
    getComponent().add(panel, BorderLayout.CENTER);
  }

  private void expandMonitor(ProfilerMonitor monitor) {
    // Track first, so current stage is sent with the event
    // TODO(terry): Needed to go from minimized to selected zoomed out view.
    //getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectMonitor();
    monitor.expand();
  }

  @Override
  public JComponent getToolbar() {
    // TODO(terry): What should I return here?
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }
}

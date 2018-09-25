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
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.profilers.*;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
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
  private static final int AXIS_SIZE = 100;

  // TODO(terry): Need to handle when we have more than one view.
  //@NotNull
  //@SuppressWarnings("FieldCanBeLocal") // We need to keep a reference to the sub-views. If they got collected, they'd stop updating the UI.
  //private final List<FlutterProfilerMonitorView> views;

  public FlutterStudioMonitorStageView(@NotNull FlutterStudioProfilersView profilersView,
                                       @NotNull FlutterStudioMonitorStage stage) {
    super(profilersView, stage);

    // TODO(terry): Need to handle when we have more than one view.
    //ViewBinder<FlutterStudioProfilersView, ProfilerMonitor, ProfilerMonitorView> binder = new ViewBinder<>();
    //binder.bind(NetworkMonitor.class, NetworkMonitorView::new);
    //binder.bind(CpuMonitor.class, CpuMonitorView::new);
    //binder.bind(MemoryMonitor.class, MemoryMonitorView::new);
    //binder.bind(EventMonitor.class, EventMonitorView::new);

    // The scrollbar can modify the view range - so it should be registered to
    // the Choreographer before all other Animatables that attempts to read the
    // same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(getTimeline(), getComponent());
    getComponent().add(sb, BorderLayout.SOUTH);
    getComponent().add(new JLabel("Your content here"), BorderLayout.CENTER);

    // Create a 2-row panel. First row, all monitors; second row, the timeline. This way, the
    // timeline will always be at the bottom, even if no monitors are found (e.g. when the phone is
    // disconnected).
    JPanel topPanel = new JPanel(new TabularLayout("*", "*,Fit-"));
    topPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    TabularLayout layout = new TabularLayout("*");
    JPanel monitors = new JPanel(layout);

    ProfilerTimeline timeline = stage.getStudioProfilers().getTimeline();

    // Use FlowLayout instead of the usual BorderLayout since BorderLayout doesn't respect min/preferred sizes.
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    RangeTooltipComponent
      tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(),
                                          timeline.getDataRange(), getTooltipPanel(),
                                          getProfilersView().getComponent(), () -> true);

    // TODO(terry): As we support other tooltips on our Flutter stage.
    //getTooltipBinder().bind(NetworkMonitorTooltip.class, NetworkMonitorTooltipView::new);
    //getTooltipBinder().bind(CpuMonitorTooltip.class, CpuMonitorTooltipView::new);
    //getTooltipBinder().bind(MemoryMonitorTooltip.class, MemoryMonitorTooltipView::new);
    //getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    //getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);

    // TODO(Terry): Needed for tooltip and other things.
    //views = new ArrayList<>(stage.getMonitors().size());
    //int rowIndex = 0;
    //for (ProfilerMonitor monitor : stage.getMonitors()) {
      //ProfilerMonitorView view = binder.build(profilersView, monitor);
      //view.registerTooltip(tooltip, stage);
      //JComponent component = view.getComponent();
      //component.addMouseListener(new MouseAdapter() {
      //  @Override
      //  public void mouseReleased(MouseEvent e) {
      //    if (SwingUtilities.isLeftMouseButton(e)) {
      //      expandMonitor(monitor);
      //    }
      //  }
      //});
      //component.addKeyListener(new KeyAdapter() {
      //  @Override
      //  public void keyTyped(KeyEvent e) {
      //    // On Windows we don't get a KeyCode so checking the getKeyCode
      //    // doesn't work. Instead we get the code from the char we are given.
      //    int keyCode = KeyEvent.getExtendedKeyCodeForChar(e.getKeyChar());
      //    if (keyCode == KeyEvent.VK_ENTER) {
      //      if (monitor.isFocused()) {
      //    expandMonitor(monitor);
      //      }
      //    }
      //  }
      //});

      // Configure Context Menu
      //IdeProfilerComponents ideProfilerComponents = getIdeComponents();
      //contextMenuInstaller contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();

      //int weight = (int)(view.getVerticalWeight() * 100f);
      //layout.setRowSizing(rowIndex, (weight > 0) ? weight + "*" : "Fit-");
      //monitors.add(component, new TabularLayout.Constraint(rowIndex, 0));
      //rowIndex++;
      //views.add(view);
    //}

    FlutterStudioProfilers profilers = stage.getStudioProfilers();
    JComponent timeAxis = buildTimeAxis(profilers);

    // TODO(terry): Build the Y-axis size of heap in bytes.
    Range yRange1Animatable = new Range(0, 100);
    final String HEAP_LABEL = "Heap";
    AxisComponent yAxisBytes;

    ResizingAxisComponentModel yAxisAxisBytesModel =
      new ResizingAxisComponentModel.Builder(yRange1Animatable, MemoryAxisFormatter.DEFAULT).setLabel(HEAP_LABEL).build();
    yAxisBytes = new AxisComponent(yAxisAxisBytesModel, AxisComponent.AxisOrientation.RIGHT);

    yAxisBytes.setShowMax(true);
    yAxisBytes.setShowUnitAtMax(true);
    yAxisBytes.setHideTickAtMin(true);
    yAxisBytes.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    yAxisBytes.setMargins(0, Y_AXIS_TOP_MARGIN);

    // TODO(terry): Better Y axis.
    /*
    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent memoryAxis = new AxisComponent(getStage().getMemoryAxis(), AxisComponent.AxisOrientation.RIGHT);
    memoryAxis.setShowAxisLine(false);
    memoryAxis.setShowMax(true);
    memoryAxis.setShowUnitAtMax(true);
    memoryAxis.setHideTickAtMin(true);
    memoryAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    memoryAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(memoryAxis, BorderLayout.WEST);

    // Then need to add yAxis to topPanel (See below).
    topPanel.add(yAxis, new TabularLayout.Constraint(0, 1));

    */

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

    JLabel label = new JLabel("Memory");
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(JLabel.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    topPanel.add(tooltip, new TabularLayout.Constraint(0, 0));

    JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);

    topPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    topPanel.add(yAxisBytes, new TabularLayout.Constraint(0, 0));
    topPanel.add(mLineChart, new TabularLayout.Constraint(0, 0));
    topPanel.add(timeAxis, new TabularLayout.Constraint(1, 0));

    getComponent().add(topPanel, BorderLayout.CENTER);
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

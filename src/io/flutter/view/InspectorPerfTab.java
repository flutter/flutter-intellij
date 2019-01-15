/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.inspector.FrameRenderingDisplay;
import io.flutter.inspector.HeapDisplay;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.perf.PerfMetric;
import io.flutter.perf.PerfReportKind;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.server.vmService.FlutterFramesMonitor;
import io.flutter.server.vmService.ServiceExtensions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class InspectorPerfTab extends JBPanel implements InspectorTabPanel {
  /**
   * Tracking widget repaints may confuse users so we disable it by default currently.
   */
  private static final boolean ENABLE_TRACK_REPAINTS = false;
  private static final boolean SHOW_MEMORY_PANEL = false;

  private static final NumberFormat fpsFormat = new DecimalFormat();

  static {
    fpsFormat.setMinimumFractionDigits(1);
    fpsFormat.setMaximumFractionDigits(1);
  }

  private final Disposable parentDisposable;
  private final @NotNull FlutterApp app;
  private final BoolServiceExtensionCheckbox showPerfOverlay;
  private final BoolServiceExtensionCheckbox showRepaintRainbow;
  private final ToolWindow toolWindow;
  private JPanel rebuildStatsPanel;
  private WidgetPerfSummaryView perfSummaryView;

  private JCheckBox trackRebuildsCheckbox;
  private JCheckBox trackRepaintsCheckbox;

  private JBSplitter treeSplitter;
  private boolean lastUseSplitter = false;

  private float lastSplitterProportion = 0.5f;
  private JPanel perfSummaryContainer;
  private JPanel perfSummaryPlaceholder;
  private JComponent currentSummaryView;

  InspectorPerfTab(Disposable parentDisposable, @NotNull FlutterApp app, ToolWindow toolWindow) {
    this.app = app;
    this.toolWindow = toolWindow;
    this.parentDisposable = parentDisposable;

    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());

    showPerfOverlay = new BoolServiceExtensionCheckbox(app, ServiceExtensions.performanceOverlay, "");
    showRepaintRainbow = new BoolServiceExtensionCheckbox(app, ServiceExtensions.repaintRainbow, "");

    buildUI();

    trackRebuildsCheckbox.setSelected(widgetPerfManager.isTrackRebuildWidgets());
    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox.setSelected(widgetPerfManager.isTrackRepaintWidgets());
    }

    app.hasServiceExtension(ServiceExtensions.trackRebuildWidgets.getExtension(), trackRebuildsCheckbox::setEnabled, parentDisposable);

    if (ENABLE_TRACK_REPAINTS) {
      app.hasServiceExtension(ServiceExtensions.trackRepaintWidgets.getExtension(), trackRepaintsCheckbox::setEnabled, parentDisposable);
    }

    trackRebuildsCheckbox.addChangeListener((l) -> {
      setTrackRebuildWidgets(trackRebuildsCheckbox.isSelected());
      updateShowPerfSummaryView();
    });

    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox.addChangeListener((l) -> {
        setTrackRepaintWidgets(trackRepaintsCheckbox.isSelected());
        updateShowPerfSummaryView();
      });
    }
  }

  void updateShowPerfSummaryView() {
    final boolean show = getShowPerfTable();
    final boolean firstRender = currentSummaryView == null;
    final JComponent summaryView = show ? perfSummaryView : perfSummaryPlaceholder;

    if (summaryView != currentSummaryView) {
      if (currentSummaryView != null) {
        perfSummaryContainer.remove(currentSummaryView);
      }
      currentSummaryView = summaryView;
      perfSummaryContainer.add(summaryView, BorderLayout.CENTER);
      perfSummaryContainer.revalidate();
      perfSummaryContainer.repaint();
    }
  }

  boolean getShowPerfTable() {
    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());
    return widgetPerfManager.isTrackRebuildWidgets() || widgetPerfManager.isTrackRebuildWidgets();
  }

  private void buildUI() {
    setLayout(new BorderLayout(0, 8));
    setBorder(JBUI.Borders.empty(3));

    // We have to set the minimum size to make "Split Mode" work well.
    // Otherwise, this tab will require the majority of the IntelliJ window
    // width largely due to the long warning message about running in debug
    // mode.
    setMinimumSize(new Dimension(0,0));

    // Header
    final JPanel footer = new JPanel(new VerticalLayout(0));
    footer.add(new JSeparator());
    final JPanel labels = new JPanel(new BorderLayout(6, 0));
    labels.setBorder(JBUI.Borders.empty(0, 8));

    final JLabel runModeLabel = new JBLabel("Running in " + app.getLaunchMode() + " mode");
    runModeLabel.setVerticalAlignment(SwingConstants.TOP);
    labels.add(
      runModeLabel,
      BorderLayout.WEST
    );

    footer.add(labels);
    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      final JBLabel label = new JBLabel("<html><body>(note: frame rendering times are not indicative of release mode performance unless run in profile mode)</body></html>");
      label.setForeground(JBColor.RED);
      labels.add(label, BorderLayout.CENTER);
    }

    treeSplitter = new JBSplitter(false, "io.flutter.view.InspectorPerfTab", lastSplitterProportion);
    add(treeSplitter, BorderLayout.CENTER);

    // FPS
    assert app.getVMServiceManager() != null;
    final FlutterFramesMonitor flutterFramesMonitor = app.getVMServiceManager().getFlutterFramesMonitor();
    final JBLabel fpsLabel = new JBLabel(" ", SwingConstants.CENTER);
    fpsLabel.setForeground(UIUtil.getLabelDisabledForeground());
    final FlutterFramesMonitor.Listener listener = event -> {
      fpsLabel.setText(fpsFormat.format(flutterFramesMonitor.getFPS()) + " frames per second");
      SwingUtilities.invokeLater(fpsLabel::repaint);
    };
    flutterFramesMonitor.addListener(listener);
    Disposer.register(parentDisposable, () -> flutterFramesMonitor.removeListener(listener));
    fpsLabel.setBorder(JBUI.Borders.empty(0, 5));

    // Frame Rendering
    final JPanel frameRenderingPanel = new JPanel(new BorderLayout());
    final JPanel frameRenderingDisplay = FrameRenderingDisplay.createJPanelView(parentDisposable, app);
    frameRenderingPanel.add(fpsLabel, BorderLayout.NORTH);
    frameRenderingPanel.add(frameRenderingDisplay, BorderLayout.CENTER);
    frameRenderingPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Frame rendering time"));

    // Memory
    JPanel memoryPanel = null;
    if (SHOW_MEMORY_PANEL) {
      memoryPanel = new JPanel(new BorderLayout());
      final JPanel heapDisplay = HeapDisplay.createJPanelView(parentDisposable, app);
      memoryPanel.add(heapDisplay, BorderLayout.CENTER);
      memoryPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Memory usage"));
    }

    // Performance settings
    final JPanel perfSettings = new JPanel(new VerticalLayout(5));
    perfSettings.add(showPerfOverlay.getComponent());
    perfSettings.add(showRepaintRainbow.getComponent());
    perfSettings.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Framework settings"));

    final JBPanel generalPerfPanel = new JBPanel(new VerticalLayout(5));
    generalPerfPanel.add(perfSettings);
    generalPerfPanel.add(frameRenderingPanel);

    final JBPanel leftColumn = new JBPanel(new BorderLayout());
    leftColumn.add(ScrollPaneFactory.createScrollPane(generalPerfPanel), BorderLayout.CENTER);
    leftColumn.add(footer, BorderLayout.SOUTH);

    if (memoryPanel != null) {
      generalPerfPanel.add(memoryPanel);
    }

    Disposer.register(parentDisposable, treeSplitter::dispose);
    treeSplitter.setFirstComponent(leftColumn);
    rebuildStatsPanel = new JPanel(new BorderLayout(0, 5));

    // rebuild stats
    perfSummaryContainer = new JPanel(new BorderLayout());
    currentSummaryView = null;
    perfSummaryPlaceholder = new JPanel(new BorderLayout());
    perfSummaryPlaceholder.add(
      ScrollPaneFactory.createScrollPane(
        new JBLabel(
          "<html><body style='padding-left:25px; padding-right:25px;'>" +
          "<p>" +
          "Widget rebuild information tells you what widgets have been " +
          "recently rebuilt on your current screen and in the source file you're " +
          "viewing." +
          "</p>" +
          "<br>" +
          "<p>" +
          "It also provides you with relevant performance advices based on the " +
          "behavior of your UI." +
          "</p>" +
          "</body></html>"
        ),
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      )
    );

    perfSummaryContainer.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Rebuild stats"));
    final JPanel perfViewSettings = new JPanel(new VerticalLayout(5));
    trackRebuildsCheckbox = new JCheckBox("Show widget rebuild information");
    trackRebuildsCheckbox.setHorizontalAlignment(JLabel.LEFT);
    trackRebuildsCheckbox.setToolTipText(
      "<html><body><p><b>This profiler identifies widgets that are rebuilt when the UI changes.</b></p>" +
      "<br>" +
      "<p>Look for the indicators on the left margin of the code editor<br>and a list of the top rebuilt widgets in this window.</p>" +
      "</body></html>");
    perfViewSettings.add(trackRebuildsCheckbox);
    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox = new JCheckBox("Show widget repaint information");
      perfViewSettings.add(trackRepaintsCheckbox);
    }
    perfViewSettings.add(new JSeparator());
    perfSummaryView = new WidgetPerfSummaryView(parentDisposable, app, PerfMetric.lastFrame, PerfReportKind.rebuild);
    perfSummaryContainer.add(perfViewSettings, BorderLayout.NORTH);

    updateShowPerfSummaryView();
    rebuildStatsPanel.add(perfSummaryContainer, BorderLayout.CENTER);

    // perf tips
    final JPanel perfTipsPanel = perfSummaryView.getWidgetPerfTipsPanel();
    rebuildStatsPanel.add(perfTipsPanel, BorderLayout.SOUTH);
    perfTipsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Performance tips"));
    perfTipsPanel.setVisible(false);

    treeSplitter.setHonorComponentsMinimumSize(false);
    treeSplitter.setSecondComponent(rebuildStatsPanel);

    toolWindow.getComponent().addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        layoutUiOnResize();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
      }

      @Override
      public void componentShown(ComponentEvent e) {
        layoutUiOnResize();
      }

      @Override
      public void componentHidden(ComponentEvent e) {
      }
    });

    layoutUiOnResize();
  }

  private void layoutUiOnResize() {
    final int windowWidth = toolWindow.getComponent().getWidth();
    final int windowHeight = toolWindow.getComponent().getHeight();
    final double aspectRatio = (double)windowWidth / (double)windowHeight;
    final boolean vertical = aspectRatio < 1.4 && windowWidth < 500;

    if (vertical != treeSplitter.getOrientation()) {
      treeSplitter.setOrientation(vertical);
    }
  }

  private void setTrackRebuildWidgets(boolean selected) {
    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());
    widgetPerfManager.setTrackRebuildWidgets(selected);
    // Update default so next app launched will match the existing setting.
    FlutterWidgetPerfManager.trackRebuildWidgetsDefault = selected;
  }

  private void setTrackRepaintWidgets(boolean selected) {
    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());
    widgetPerfManager.setTrackRepaintWidgets(selected);
    // Update default so next app launched will match the existing setting.
    FlutterWidgetPerfManager.trackRepaintWidgetsDefault = selected;
  }

  @Override
  public void setVisibleToUser(boolean visible) {
    assert app.getVMServiceManager() != null;

    perfSummaryView.setVisibleToUser(visible);

    if (visible) {
      app.getVMServiceManager().addPollingClient();
    }
    else {
      app.getVMServiceManager().removePollingClient();
    }
  }
}

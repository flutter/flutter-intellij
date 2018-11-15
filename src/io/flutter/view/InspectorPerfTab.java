/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.*;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.server.vmService.FlutterFramesMonitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

import static io.flutter.view.PerformanceOverlayAction.SHOW_PERFORMANCE_OVERLAY;
import static io.flutter.view.RepaintRainbowAction.SHOW_REPAINT_RAINBOW;

public class InspectorPerfTab extends JBPanel implements InspectorTabPanel {
  /**
   * Tracking widget repaints may confuse users so we disable it by default
   * currently.
   */
  private static final boolean ENABLE_TRACK_REPAINTS = false;

  private final Disposable parentDisposable;
  private final @NotNull FlutterApp app;
  private final BoolServiceExtensionCheckbox showPerfOverlay;
  private final BoolServiceExtensionCheckbox showRepaintRainbow;

  private JCheckBox trackRebuildsCheckbox;
  private JCheckBox trackRepaintsCheckbox;
  private WidgetPerfPanel widgetPerfPanel;

  InspectorPerfTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;
    this.parentDisposable = parentDisposable;

    showPerfOverlay = new BoolServiceExtensionCheckbox(app, SHOW_PERFORMANCE_OVERLAY, "Show Performance Overlay", "");
    showRepaintRainbow = new BoolServiceExtensionCheckbox(app, SHOW_REPAINT_RAINBOW, "Show Repaint Rainbow", "");

    buildUI();

    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());
    trackRebuildsCheckbox.setSelected(widgetPerfManager.isTrackRebuildWidgets());
    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox.setSelected(widgetPerfManager.isTrackRepaintWidgets());
    }

    app.hasServiceExtension(FlutterWidgetPerfManager.TRACK_REBUILD_WIDGETS, trackRebuildsCheckbox::setEnabled, parentDisposable);
    if (ENABLE_TRACK_REPAINTS) {
      app.hasServiceExtension(FlutterWidgetPerfManager.TRACK_REPAINT_WIDGETS, trackRepaintsCheckbox::setEnabled, parentDisposable);
    }

    trackRebuildsCheckbox.addChangeListener((l) -> setTrackRebuildWidgets(trackRebuildsCheckbox.isSelected()));

    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox.addChangeListener((l) -> setTrackRepaintWidgets(trackRepaintsCheckbox.isSelected()));
    }
  }

  private void buildUI() {
    setLayout(new BorderLayout());

    // Header
    final JPanel labels = new JPanel(new BorderLayout(6, 0));
    labels.setBorder(JBUI.Borders.empty(3, 10));
    add(labels, BorderLayout.NORTH);

    labels.add(
      new JBLabel("Running in " + app.getLaunchMode() + " mode"),
      BorderLayout.WEST);

    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      final JBLabel label = new JBLabel("(note: for best results, re-run in profile mode)");
      label.setForeground(JBColor.RED);
      labels.add(label, BorderLayout.CENTER);
    }

    // FPS
    assert app.getVMServiceManager() != null;
    final FlutterFramesMonitor flutterFramesMonitor = app.getVMServiceManager().getFlutterFramesMonitor();
    final JBLabel fpsLabel = new JBLabel("Frames per second: ");
    final FlutterFramesMonitor.Listener listener = event -> {
      fpsLabel.setText("Frames per second: " + new DecimalFormat().format(flutterFramesMonitor.getFPS()));
      SwingUtilities.invokeLater(fpsLabel::repaint);
    };
    flutterFramesMonitor.addListener(listener);
    Disposer.register(parentDisposable, () -> flutterFramesMonitor.removeListener(listener));
    fpsLabel.setBorder(JBUI.Borders.empty(0, 5));

    // Frame Rendering
    final JPanel frameRenderingPanel = new JPanel(new BorderLayout());
    final JPanel frameRenderingDisplay = FrameRenderingDisplay.createJPanelView(parentDisposable, app);
    frameRenderingPanel.add(frameRenderingDisplay, BorderLayout.CENTER);
    frameRenderingPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Frame Rendering Time"));

    // Memory
    final JPanel memoryPanel = new JPanel(new BorderLayout());
    final JPanel heapDisplay = HeapDisplay.createJPanelView(parentDisposable, app);
    memoryPanel.add(heapDisplay, BorderLayout.CENTER);
    memoryPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Memory"));

    // Performance settings
    final JPanel perfSettings = new JPanel(new VerticalLayout(5));
    trackRebuildsCheckbox = new JCheckBox("Show widget rebuild indicators");
    trackRebuildsCheckbox.setHorizontalAlignment(JLabel.LEFT);
    trackRebuildsCheckbox.setToolTipText(
      "Rebuild Indicators appear on each line of code where the widget is being rebuilt by Flutter. Rebuilding widgets is generally very cheap. You should only worry about optimizing code to reduce the number of widget rebuilds if you notice that the frame rate is below 60fps or if widgets that you did not expect to be rebuilt are rebuilt a very large number of times.");
    perfSettings.add(trackRebuildsCheckbox);
    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox = new JCheckBox("Show widget repaint indicators");
      perfSettings.add(trackRepaintsCheckbox);
    }
    perfSettings.add(showRepaintRainbow.getComponent());
    perfSettings.add(showPerfOverlay.getComponent());

    // Perf info and tips
    widgetPerfPanel = new WidgetPerfPanel(parentDisposable, app);

    final JPanel leftColumn = new JPanel(new VerticalLayout(5));
    leftColumn.add(fpsLabel);
    leftColumn.add(frameRenderingPanel);
    leftColumn.add(memoryPanel);

    final JPanel rightColumn = new JPanel(new VerticalLayout(5));
    rightColumn.add(perfSettings);
    rightColumn.add(widgetPerfPanel);

    final JPanel bodyPanel = new JPanel(new GridLayout(1, 2, 5, 5));
    bodyPanel.setBorder(JBUI.Borders.empty(5));
    bodyPanel.add(leftColumn);
    bodyPanel.add(rightColumn);
    add(bodyPanel, BorderLayout.CENTER);
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

  public WidgetPerfPanel getWidgetPerfPanel() {
    return widgetPerfPanel;
  }

  @Override
  public void setVisibleToUser(boolean visible) {
    assert app.getVMServiceManager() != null;

    widgetPerfPanel.setVisibleToUser(visible);

    if (visible) {
      app.getVMServiceManager().addPollingClient();
    }
    else {
      app.getVMServiceManager().removePollingClient();
    }
  }
}

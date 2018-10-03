/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.FPSDisplay;
import io.flutter.inspector.HeapDisplay;
import io.flutter.inspector.WidgetPerfPanel;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InspectorPerfTab extends JBPanel implements InspectorTabPanel {
  private final Disposable parentDisposable;
  private final @NotNull FlutterApp app;

  private JCheckBox trackRebuildsCheckbox;
  private JCheckBox trackRepaintsCheckbox;
  private WidgetPerfPanel widgetPerfPanel;

  InspectorPerfTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;
    this.parentDisposable = parentDisposable;

    buildUI();

    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());
    trackRebuildsCheckbox.setSelected(widgetPerfManager.isTrackRebuildWidgets());
    trackRepaintsCheckbox.setSelected(widgetPerfManager.isTrackRepaintWidgets());

    app.hasServiceExtension(FlutterWidgetPerfManager.TRACK_REBUILD_WIDGETS, trackRebuildsCheckbox::setEnabled, parentDisposable);
    app.hasServiceExtension(FlutterWidgetPerfManager.TRACK_REPAINT_WIDGETS, trackRepaintsCheckbox::setEnabled, parentDisposable);

    trackRebuildsCheckbox.addChangeListener((l) -> {
      setTrackRebuildWidgets(trackRebuildsCheckbox.isSelected());
    });

    trackRepaintsCheckbox.addChangeListener((l) -> {
      setTrackRepaintWidgets(trackRepaintsCheckbox.isSelected());
    });
  }

  private void buildUI() {
    setLayout(new GridBagLayout());
    setBorder(JBUI.Borders.empty(5));

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.BOTH;

    // header
    final JPanel headerPanel = new JPanel(new BorderLayout(0, 3));
    headerPanel.add(new JBLabel("Running in " + app.getLaunchMode() + " mode"), BorderLayout.NORTH);
    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      headerPanel.add(
        new JBLabel("<html><body><p style='color:red'>Note: for best results, re-run in profile mode</p></body></html>"),
        BorderLayout.SOUTH
      );
    }
    headerPanel.setBorder(JBUI.Borders.empty(5));
    add(headerPanel, constraints);

    // FPS
    final JPanel fpsPanel = new JPanel(new BorderLayout());
    final JPanel fpsDisplay = FPSDisplay.createJPanelView(parentDisposable, app);
    fpsPanel.add(fpsDisplay, BorderLayout.CENTER);
    fpsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "FPS"));
    constraints.gridy = 1;
    add(fpsPanel, constraints);

    // Memory
    final JPanel memoryPanel = new JPanel(new BorderLayout());
    final JPanel heapDisplay = HeapDisplay.createJPanelView(parentDisposable, app);
    memoryPanel.add(heapDisplay, BorderLayout.CENTER);
    memoryPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Memory"));
    constraints.gridy = 2;
    add(memoryPanel, constraints);

    // Widgets stats (experimental)
    final Box widgetsPanel = Box.createVerticalBox();
    trackRebuildsCheckbox = new JCheckBox("Track widget rebuilds");
    widgetsPanel.add(trackRebuildsCheckbox);
    trackRepaintsCheckbox = new JCheckBox("Track widget repaints");
    widgetsPanel.add(trackRepaintsCheckbox);
    widgetPerfPanel = new WidgetPerfPanel(parentDisposable, app);
    widgetsPanel.add(widgetPerfPanel);
    widgetsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Widgets stats (experimental)"));
    constraints.gridy = 3;
    constraints.weighty = 1.0;
    add(widgetsPanel, constraints);
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

    if (visible) {
      app.getVMServiceManager().addPollingClient();
    }
    else {
      app.getVMServiceManager().removePollingClient();
    }
  }
}

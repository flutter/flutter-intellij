/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
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
  private JPanel mainPanel;
  private JPanel fpsDisplay;
  private JPanel memory;
  private WidgetPerfPanel widgetPerfPanel;
  private JCheckBox trackRebuildsCheckbox;
  private JCheckBox trackRepaintsCheckbox;
  private JLabel modeLabel;
  private JLabel warningLabel;

  InspectorPerfTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;
    this.parentDisposable = parentDisposable;

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
    setLayout(new BorderLayout());
    add(mainPanel, BorderLayout.CENTER);
  }

  private void createUIComponents() {
    fpsDisplay = FPSDisplay.createJPanelView(parentDisposable, app);
    memory = HeapDisplay.createJPanelView(parentDisposable, app);
    widgetPerfPanel = new WidgetPerfPanel(parentDisposable, app);

    modeLabel = new JBLabel("Running in " + app.getLaunchMode() + " mode");

    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      warningLabel = new JBLabel("<html><body><p style='color:red'>WARNING: for best results, re-run in profile mode</p></body></html>");
    }
    else {
      warningLabel = new JBLabel("");
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
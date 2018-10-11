/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import io.flutter.FlutterBundle;
import io.flutter.inspector.*;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

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
    setLayout(new VerticalLayout(5));
    setBorder(JBUI.Borders.empty(5));

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
    add(headerPanel);

    // FPS
    final JPanel fpsPanel = new JPanel(new BorderLayout());
    final JPanel fpsDisplay = FPSDisplay.createJPanelView(parentDisposable, app);
    fpsPanel.add(fpsDisplay, BorderLayout.CENTER);
    fpsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "FPS"));
    add(fpsPanel);

    // Memory
    final JPanel memoryPanel = new JPanel(new BorderLayout());
    final JPanel heapDisplay = HeapDisplay.createJPanelView(parentDisposable, app);
    memoryPanel.add(heapDisplay, BorderLayout.CENTER);
    memoryPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Memory"));
    add(memoryPanel);

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
    add(perfSettings);

    widgetPerfPanel = new WidgetPerfPanel(parentDisposable, app);
    add(widgetPerfPanel);
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

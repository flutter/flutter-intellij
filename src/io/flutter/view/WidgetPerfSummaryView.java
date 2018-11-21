/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.WidgetPerfTipsPanel;
import io.flutter.perf.*;
import io.flutter.run.daemon.FlutterApp;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;

class WidgetPerfSummaryView extends JPanel {
  private static final int REFRESH_TABLE_DELAY = 100;
  private final FlutterApp app;
  private final FlutterWidgetPerfManager perfManager;
  private final Timer refreshTableTimer;
  private final WidgetPerfTable table;
  private final PerfReportKind reportKind;
  private final JBLabel tableTitle;
  private final WidgetPerfTipsPanel myWidgetPerfTipsPanel;
  private boolean visible = true;

  long lastUpdateTime;

  WidgetPerfSummaryView(Disposable parentDisposable, FlutterApp app, PerfMetric metric, PerfReportKind reportKind) {
    setLayout(new BorderLayout());

    this.app = app;
    this.reportKind = reportKind;

    perfManager = FlutterWidgetPerfManager.getInstance(app.getProject());

    refreshTableTimer = new Timer(REFRESH_TABLE_DELAY, this::onUpdateTable);
    refreshTableTimer.start();

    table = new WidgetPerfTable(app, parentDisposable, metric);

    perfManager.getCurrentStats().addPerfListener(table);
    final JPanel header = new JPanel();
    header.setLayout(new VerticalLayout(5));
    header.setBorder(JBUI.Borders.empty(5));
    add(header, BorderLayout.NORTH);
    // Update header message.
    tableTitle = new JBLabel();
    setTableTitle(reportKind);
    header.add(tableTitle);

    add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);

    // Perf info and tips
    myWidgetPerfTipsPanel = new WidgetPerfTipsPanel(parentDisposable, app);
    add(myWidgetPerfTipsPanel, BorderLayout.SOUTH);

    Disposer.register(parentDisposable, refreshTableTimer::stop);
  }

  private void setTableTitle(PerfReportKind kind) {
    final String name = reportKind.name;
    final String upperCaseName = name.substring(0, 1).toUpperCase() + name.substring(1);
    tableTitle.setText("Widget " + upperCaseName + " Stats for the Current Screen");
  }

  private void onUpdateTable(ActionEvent event) {
    final FlutterWidgetPerf stats = perfManager.getCurrentStats();
    if (stats != null) {
      final long latestPerfUpdate = stats.getLastLocalPerfEventTime();
      // Only do work if new performance stats have been recorded.
      if (latestPerfUpdate != lastUpdateTime) {
        lastUpdateTime = latestPerfUpdate;
        table.showStats(stats.getStatsForMetric(table.getMetrics(), reportKind));
      }
    }
  }

  public void setVisibleToUser(boolean visible) {
    myWidgetPerfTipsPanel.setVisibleToUser(visible);
    if (visible != this.visible) {
      this.visible = visible;
      if (visible) {
        // Reset last update time to ensure performance tips will be recomputed
        // the next time onComputePerfTips is called.
        lastUpdateTime = -1;
        refreshTableTimer.start();
      }
      else {
        refreshTableTimer.stop();
      }
    }
  }
}

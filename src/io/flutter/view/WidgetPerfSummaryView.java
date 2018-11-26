/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import io.flutter.inspector.WidgetPerfTipsPanel;
import io.flutter.perf.FlutterWidgetPerf;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.perf.PerfMetric;
import io.flutter.perf.PerfReportKind;
import io.flutter.run.daemon.FlutterApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

class WidgetPerfSummaryView extends JPanel {
  private static final int REFRESH_TABLE_DELAY = 100;
  private final FlutterApp app;
  private final FlutterWidgetPerfManager perfManager;
  private final Timer refreshTableTimer;
  private final WidgetPerfTable table;
  private final PerfReportKind reportKind;

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

    add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);

    // Perf info and tips
    myWidgetPerfTipsPanel = new WidgetPerfTipsPanel(parentDisposable, app);

    Disposer.register(parentDisposable, refreshTableTimer::stop);
  }

  public WidgetPerfTipsPanel getWidgetPerfTipsPanel() {
    return myWidgetPerfTipsPanel;
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

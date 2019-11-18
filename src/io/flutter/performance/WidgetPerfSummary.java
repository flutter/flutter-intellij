/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.performance;

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

class WidgetPerfSummary extends JPanel implements Disposable {
  private static final int REFRESH_TABLE_DELAY = 100;

  private final FlutterWidgetPerfManager perfManager;
  private final Timer refreshTableTimer;
  private final WidgetPerfTable table;
  private final PerfReportKind reportKind;

  private final WidgetPerfTipsPanel myWidgetPerfTipsPanel;

  private long lastUpdateTime;

  WidgetPerfSummary(Disposable parentDisposable, FlutterApp app, PerfMetric metric, PerfReportKind reportKind) {
    setLayout(new BorderLayout());

    this.reportKind = reportKind;

    perfManager = FlutterWidgetPerfManager.getInstance(app.getProject());

    refreshTableTimer = new Timer(REFRESH_TABLE_DELAY, this::onUpdateTable);
    refreshTableTimer.start();

    table = new WidgetPerfTable(app, parentDisposable, metric);

    Disposer.register(parentDisposable, this);

    perfManager.addPerfListener(table);

    add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER);

    // Perf info and tips
    myWidgetPerfTipsPanel = new WidgetPerfTipsPanel(parentDisposable, app);
  }

  public void dispose() {
    perfManager.removePerfListener(table);
    refreshTableTimer.stop();
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

  public void clearPerformanceTips() {
    myWidgetPerfTipsPanel.clearTips();
  }
}

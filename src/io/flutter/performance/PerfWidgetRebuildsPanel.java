/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.performance;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.perf.PerfMetric;
import io.flutter.perf.PerfReportKind;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.vmService.ServiceExtensions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class PerfWidgetRebuildsPanel extends JBPanel<PerfWidgetRebuildsPanel> {
  private static final Logger LOG = Logger.getInstance(PerfWidgetRebuildsPanel.class);

  private static final String REBUILD_STATS_TAB_LABEL = "Widget rebuild stats";

  /**
   * Tracking widget repaints may confuse users so we disable it by default currently.
   */
  private static final boolean ENABLE_TRACK_REPAINTS = false;

  private @NotNull final FlutterApp app;

  private final WidgetPerfSummary perfSummary;

  private final JCheckBox trackRebuildsCheckbox;
  private JCheckBox trackRepaintsCheckbox;

  private final JPanel perfSummaryContainer;
  private final JPanel perfSummaryPlaceholder;

  private JComponent currentSummaryView;

  PerfWidgetRebuildsPanel(@NotNull FlutterApp app, @NotNull Disposable parentDisposable) {
    this.app = app;

    setLayout(new BorderLayout());
    setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), REBUILD_STATS_TAB_LABEL));
    setPreferredSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

    final JPanel rebuildStatsPanel = new JPanel(new BorderLayout(0, 5));
    add(rebuildStatsPanel, BorderLayout.CENTER);

    // rebuild stats
    perfSummaryContainer = new JPanel(new BorderLayout(0, 5));
    currentSummaryView = null;
    perfSummaryPlaceholder = new JPanel(new BorderLayout());
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(
      new JBLabel(
        "<html><body style='padding-left:15px; padding-right:15px;'>" +
        "Widget rebuild information tells you what widgets have been " +
        "recently rebuilt for your application's current screen." +
        "<br>" +
        "</body></html>"),
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
    scrollPane.setBorder(JBUI.Borders.empty());
    scrollPane.setViewportBorder(JBUI.Borders.empty());
    perfSummaryPlaceholder.add(scrollPane);

    final JPanel perfViewSettings = new JPanel(new VerticalLayout(5, 4));
    trackRebuildsCheckbox = new JCheckBox("Track widget rebuilds");
    trackRebuildsCheckbox.setHorizontalAlignment(JLabel.RIGHT);
    trackRebuildsCheckbox.setToolTipText(
      "<html><body><p><b>This profiler identifies widgets that are rebuilt when the UI changes.</b></p>" +
      "<br>" +
      "<p>Look for the indicators on the left margin of the code editor<br>and a list of the top rebuilt widgets in this window.</p>" +
      "</body></html>");
    perfViewSettings.add(trackRebuildsCheckbox);
    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox = new JCheckBox("Show widget repaint information");
      trackRepaintsCheckbox.setHorizontalAlignment(JLabel.RIGHT);
      perfViewSettings.add(trackRepaintsCheckbox);
    }
    perfViewSettings.add(new JSeparator());
    perfSummary = new WidgetPerfSummary(parentDisposable, app, PerfMetric.lastFrame, PerfReportKind.rebuild);
    perfSummaryContainer.add(perfViewSettings, BorderLayout.NORTH);

    updateShowPerfSummaryView();
    rebuildStatsPanel.add(perfSummaryContainer, BorderLayout.CENTER);

    // perf tips
    final JPanel perfTipsPanel = perfSummary.getWidgetPerfTipsPanel();
    rebuildStatsPanel.add(perfTipsPanel, BorderLayout.SOUTH);
    perfTipsPanel.setVisible(false);

    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());

    trackRebuildsCheckbox.setSelected(widgetPerfManager.isTrackRebuildWidgets());
    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox.setSelected(widgetPerfManager.isTrackRepaintWidgets());
    }

    app.hasServiceExtension(ServiceExtensions.trackRebuildWidgets.getExtension(), trackRebuildsCheckbox::setEnabled, parentDisposable);

    if (ENABLE_TRACK_REPAINTS) {
      app.hasServiceExtension(ServiceExtensions.trackRepaintWidgets.getExtension(), trackRepaintsCheckbox::setEnabled, parentDisposable);
    }

    trackRebuildsCheckbox.addChangeListener((l) -> {
      if (app.getProject().isDisposed()) return;

      setTrackRebuildWidgets(trackRebuildsCheckbox.isSelected());
      updateShowPerfSummaryView();
    });

    if (ENABLE_TRACK_REPAINTS) {
      trackRepaintsCheckbox.addChangeListener((l) -> {
        if (app.getProject().isDisposed()) return;

        setTrackRepaintWidgets(trackRepaintsCheckbox.isSelected());
        updateShowPerfSummaryView();
      });
    }
  }

  void updateShowPerfSummaryView() {
    final boolean show = getShowPerfTable();
    final boolean firstRender = currentSummaryView == null;
    final JComponent summaryView = show ? perfSummary : perfSummaryPlaceholder;

    if (summaryView != currentSummaryView) {
      if (currentSummaryView != null) {
        perfSummaryContainer.remove(currentSummaryView);
      }
      currentSummaryView = summaryView;
      perfSummaryContainer.add(summaryView, BorderLayout.CENTER);
      perfSummaryContainer.revalidate();
      perfSummaryContainer.repaint();
    }

    if (!show) {
      perfSummary.clearPerformanceTips();
    }
  }

  boolean getShowPerfTable() {
    final FlutterWidgetPerfManager widgetPerfManager = FlutterWidgetPerfManager.getInstance(app.getProject());
    return widgetPerfManager.isTrackRebuildWidgets() || widgetPerfManager.isTrackRepaintWidgets();
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
}

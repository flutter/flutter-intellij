/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.inspector.FrameRenderingDisplay;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.server.vmService.FlutterFramesMonitor;
import io.flutter.server.vmService.ServiceExtensions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;

// TODO(devoncarew): have an 'open in devtools' UI

public class PerfFPSTab extends JBPanel implements InspectorTabPanel {
  private static final NumberFormat fpsFormat = new DecimalFormat();

  static {
    fpsFormat.setMinimumFractionDigits(1);
    fpsFormat.setMaximumFractionDigits(1);
  }

  private final Disposable parentDisposable;
  private final @NotNull FlutterApp app;
  private final BoolServiceExtensionCheckbox showPerfOverlay;
  private final BoolServiceExtensionCheckbox showRepaintRainbow;

  PerfFPSTab(Disposable parentDisposable, @NotNull FlutterApp app, ToolWindow toolWindow) {
    this.app = app;
    this.parentDisposable = parentDisposable;

    showPerfOverlay = new BoolServiceExtensionCheckbox(app, ServiceExtensions.performanceOverlay, "");
    showRepaintRainbow = new BoolServiceExtensionCheckbox(app, ServiceExtensions.repaintRainbow, "");

    buildUI();
  }

  private void buildUI() {
    setLayout(new BorderLayout(0, 3));
    setBorder(JBUI.Borders.empty(3));

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

    // Performance settings
    final JPanel perfSettings = new JPanel(new VerticalLayout(5));
    perfSettings.add(showPerfOverlay.getComponent());
    perfSettings.add(showRepaintRainbow.getComponent());
    perfSettings.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Framework settings"));

    final JBPanel generalPerfPanel = new JBPanel(new BorderLayout());
    generalPerfPanel.add(perfSettings, BorderLayout.NORTH);
    generalPerfPanel.add(frameRenderingPanel, BorderLayout.CENTER);

    final JBPanel leftColumn = new JBPanel(new BorderLayout());
    leftColumn.add(ScrollPaneFactory.createScrollPane(generalPerfPanel), BorderLayout.CENTER);

    add(leftColumn, BorderLayout.CENTER);
  }

  @Override
  public void setVisibleToUser(boolean visible) {
  }
}

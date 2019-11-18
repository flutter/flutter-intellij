/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.performance;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.inspector.FrameRenderingDisplay;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.vmService.FlutterFramesMonitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class PerfFPSPanel extends JBPanel {
  private static final NumberFormat fpsFormat = new DecimalFormat();

  private static final String PERFORMANCE_TAB_LABEL = "Frame rendering times";

  static {
    fpsFormat.setMinimumFractionDigits(1);
    fpsFormat.setMaximumFractionDigits(1);
  }

  private final Disposable parentDisposable;
  private final @NotNull FlutterApp app;

  PerfFPSPanel(@NotNull FlutterApp app, @NotNull Disposable parentDisposable) {
    this.app = app;
    this.parentDisposable = parentDisposable;

    buildUI();
  }

  private void buildUI() {
    setLayout(new BorderLayout(0, 3));
    setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), PERFORMANCE_TAB_LABEL));
    setMinimumSize(new Dimension(0, PerfMemoryPanel.HEIGHT));
    setPreferredSize(new Dimension(Short.MAX_VALUE, PerfMemoryPanel.HEIGHT));

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

    add(frameRenderingPanel, BorderLayout.CENTER);
  }
}

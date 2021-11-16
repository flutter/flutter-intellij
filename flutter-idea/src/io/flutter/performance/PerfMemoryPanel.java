/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.performance;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import io.flutter.inspector.HeapDisplay;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class PerfMemoryPanel extends JBPanel<PerfMemoryPanel> {
  private static final Logger LOG = Logger.getInstance(PerfMemoryPanel.class);

  private static final String MEMORY_TAB_LABEL = "Memory usage";

  static final int HEIGHT = 140;

  PerfMemoryPanel(@NotNull FlutterApp app, @NotNull Disposable parentDisposable) {
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), MEMORY_TAB_LABEL));
    setMinimumSize(new Dimension(0, PerfMemoryPanel.HEIGHT));
    setPreferredSize(new Dimension(Short.MAX_VALUE, PerfMemoryPanel.HEIGHT));

    final JPanel heapDisplay = HeapDisplay.createJPanelView(parentDisposable, app);
    add(heapDisplay, BorderLayout.CENTER);

    if (app.getVMServiceManager() != null) {
      app.getVMServiceManager().getHeapMonitor().addPollingClient();
    }

    Disposer.register(parentDisposable, () -> {
      if (app.getVMServiceManager() != null) {
        app.getVMServiceManager().getHeapMonitor().removePollingClient();
      }
    });
  }
}

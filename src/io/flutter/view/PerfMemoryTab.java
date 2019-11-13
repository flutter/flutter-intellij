/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBUI;
import io.flutter.devtools.DevToolsManager;
import io.flutter.inspector.HeapDisplay;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class PerfMemoryTab extends JBPanel implements InspectorTabPanel {
  private static final Logger LOG = Logger.getInstance(PerfMemoryTab.class);

  private @NotNull final FlutterApp app;

  PerfMemoryTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new BorderLayout());
    setBorder(JBUI.Borders.empty(3));

    final JPanel linkPanel = new JPanel(new BorderLayout());
    final LinkLabel openDevtools = new LinkLabel("Open in DevTools", null);
    //noinspection unchecked
    openDevtools.setListener((linkLabel, data) -> openInDevTools(), null);
    linkPanel.add(BorderLayout.EAST, openDevtools);
    linkPanel.setBorder(JBUI.Borders.empty(3, 5));
    add(linkPanel, BorderLayout.NORTH);

    final JPanel memoryPanel = new JPanel(new BorderLayout());
    final JPanel heapDisplay = HeapDisplay.createJPanelView(parentDisposable, app);
    memoryPanel.add(heapDisplay, BorderLayout.CENTER);
    memoryPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Memory usage"));
    add(memoryPanel, BorderLayout.CENTER);
  }

  private void openInDevTools() {
    // open the memory view
    final DevToolsManager devToolsManager = DevToolsManager.getInstance(app.getProject());
    devToolsManager.openToScreen(app, "memory");
  }

  @Override
  public void setVisibleToUser(boolean visible) {
    if (app.getVMServiceManager() == null) {
      return;
    }

    if (visible) {
      app.getVMServiceManager().getHeapMonitor().addPollingClient();
    }
    else {
      app.getVMServiceManager().getHeapMonitor().removePollingClient();
    }
  }
}

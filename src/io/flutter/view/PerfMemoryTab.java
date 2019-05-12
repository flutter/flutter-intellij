/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.HeapDisplay;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

// TODO(devoncarew): have an 'open in devtools' UI

public class PerfMemoryTab extends JBPanel implements InspectorTabPanel {
  private static final Logger LOG = Logger.getInstance(PerfMemoryTab.class);

  private @NotNull final FlutterApp app;

  PerfMemoryTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new BorderLayout());
    setBorder(JBUI.Borders.empty(3));

    // todo: is this necessary?
    app.getVMServiceManager().addPollingClient();

    final JPanel memoryPanel = new JPanel(new BorderLayout());
    final JPanel heapDisplay = HeapDisplay.createJPanelView(parentDisposable, app);
    memoryPanel.add(heapDisplay, BorderLayout.CENTER);
    memoryPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Memory usage"));

    add(memoryPanel, BorderLayout.CENTER);
  }

  @Override
  public void finalize() {
    // Done collecting for the memory profiler - if this instance is GC'd.
    assert app.getVMServiceManager() != null;

    // TODO: ???
    app.getVMServiceManager().removePollingClient();
  }

  @Override
  public void setVisibleToUser(boolean visible) {
  }
}

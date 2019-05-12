/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InspectorMemoryTab extends JPanel implements InspectorTabPanel {
  private static final Logger LOG = Logger.getInstance(InspectorMemoryTab.class);

  private @NotNull final FlutterApp app;

  InspectorMemoryTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new BorderLayout());

    // TODO: ??? add polling client

    final JLabel warningLabel = new JLabel(
      "Todo:", null, SwingConstants.CENTER);
    warningLabel.setFont(JBFont.create(warningLabel.getFont()).asBold());
    warningLabel.setBorder(JBUI.Borders.empty(3, 10));
    add(warningLabel, BorderLayout.CENTER);
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

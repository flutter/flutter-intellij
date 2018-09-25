/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.FPSDisplay;
import io.flutter.inspector.HeapDisplay;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InspectorPerfTab extends JPanel implements InspectorTabPanel {
  private @NotNull FlutterApp app;

  InspectorPerfTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    add(Box.createVerticalStrut(6));

    Box labelBox = Box.createHorizontalBox();
    labelBox.add(new JLabel("Running in " + app.getLaunchMode() + " mode"));
    labelBox.add(Box.createHorizontalGlue());
    labelBox.setBorder(JBUI.Borders.empty(3, 10));
    add(labelBox);

    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      labelBox = Box.createHorizontalBox();
      labelBox.add(new JLabel("Note: for best results, re-run in profile mode"));
      labelBox.add(Box.createHorizontalGlue());
      labelBox.setBorder(JBUI.Borders.empty(3, 10));
      add(labelBox);
    }

    add(Box.createVerticalStrut(6));

    add(FPSDisplay.createJPanelView(parentDisposable, app), BorderLayout.NORTH);
    add(Box.createVerticalStrut(16));
    add(HeapDisplay.createJPanelView(parentDisposable, app), BorderLayout.SOUTH);
    add(Box.createVerticalGlue());
  }

  @Override
  public void setVisibleToUser(boolean visible) {
    assert app.getPerfService() != null;

    if (visible) {
      app.getPerfService().addPollingClient();
    }
    else {
      app.getPerfService().removePollingClient();
    }
  }
}
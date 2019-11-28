/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class FlutterSampleActionsPanel extends EditorNotificationPanel {
  FlutterSampleActionsPanel(@NotNull List<FlutterSample> samples) {
    super(EditorColors.GUTTER_BACKGROUND);

    icon(FlutterIcons.Flutter);
    text("View hosted code sample");

    for (int i = 0; i < samples.size(); i++) {
      if (i != 0) {
        myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      }

      final FlutterSample sample = samples.get(i);

      final HyperlinkLabel label = createActionLabel(sample.getClassName(), () -> browseTo(sample));
      label.setToolTipText(sample.getHostedDocsUrl());
    }
  }

  private void browseTo(FlutterSample sample) {
    BrowserUtil.browse(sample.getHostedDocsUrl());
  }
}

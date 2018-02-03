/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import org.dartlang.analysis.server.protocol.Element;

import javax.swing.*;
import java.awt.*;

// TODO: set is hidden or is shown

public class PreviewAreaPanel extends JPanel {
  private final JLabel label;

  public PreviewAreaPanel() {
    super(new BorderLayout());

    label = new JLabel("TODO:", SwingConstants.CENTER);

    add(label, BorderLayout.CENTER);
  }

  public void updatePreviewElement(Element element) {
    // TODO:

    if (element == null) {
      setText("No widget selected");
    }
    else {
      // TODO: how to find the parent?
      setText(element.getName() + "(): " + element.getLocation());
    }
  }

  void setText(String text) {
    label.setText(text);
  }
}

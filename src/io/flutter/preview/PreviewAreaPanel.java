/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import org.dartlang.analysis.server.protocol.Element;

import javax.swing.*;
import java.awt.*;

// TODO: have a way to tell the panel whether it's hidden or shown

// TODO: we'll need to know whether the preview element is stateless widget or a state of a stateful
//       widget; for the 2nd case, we'll need to find the cooresponding stateful widget class

// TODO: we want to preview anything in a state, stateful, or stateless class (not
//       just things contained in a build method)

// TODO: we should be bolding stateful and stateless (and state) classes, not build() methods
//       or, show all elements of these classes with some additional emphasis (italic? background color?)

public class PreviewAreaPanel extends JPanel {
  private final JLabel label;

  public PreviewAreaPanel() {
    super(new BorderLayout());

    label = new JLabel("TODO:", SwingConstants.CENTER);

    add(label, BorderLayout.CENTER);
  }

  public void updatePreviewElement(Element parentElement, Element methodElement) {
    // TODO:

    if (parentElement == null) {
      setText("No widget selected");
    }
    else {
      setText(parentElement.getName() + "." + methodElement.getName() + "()");
    }
  }

  void setText(String text) {
    label.setText(text);
  }
}

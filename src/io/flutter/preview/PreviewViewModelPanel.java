/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.openapi.ui.popup.Balloon;
import io.flutter.editor.PreviewViewController;
import io.flutter.editor.PreviewViewControllerBase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Class that provides the glue to render the preview view in a regular JPanel.
 */
public class PreviewViewModelPanel extends JPanel {
  final PreviewViewControllerBase preview;

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    preview.paint(g, 16);
  }

  public PreviewViewModelPanel(PreviewViewController preview) {
    this.preview = preview;

    addMouseMotionListener(new MouseMotionListener() {

      @Override
      public void mouseDragged(MouseEvent e) {
        // The PreviewViewController does not care about drag events.
        // If it ever does, this code will need to be updated.
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        preview.onMouseMoved(e);
      }
    });
    addMouseListener(new MouseListener() {
      @Override
      public void mouseClicked(MouseEvent e) {
        // The PreviewViewController does not care about click events instead
        // relying on mouseReleased events.
        // If it ever does, this code will need to be updated.
      }

      @Override
      public void mousePressed(MouseEvent e) {
        preview.onMousePressed(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        preview.onMouseReleased(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        preview.onMouseEntered(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        preview.onMouseExited(e);
      }
    });
  }
}

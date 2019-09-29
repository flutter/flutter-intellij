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

public class PreviewViewModelPanel extends JPanel {
  final PreviewViewControllerBase preview;

  Balloon popup;

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

      }

      @Override
      public void mouseMoved(MouseEvent e) {
        preview.onMouseMoved(e);
      }
    });
    addMouseListener(new MouseListener() {
      @Override
      public void mouseClicked(MouseEvent e) {
      }

      @Override
      public void mousePressed(MouseEvent e) {
        // TODO(jacobr): verify popup case.
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

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.MouseInputAdapter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * JBTable custom selection and hover renderer for a table view.
 */
public class HighlightedTable extends JBTable {
  final static private JBColor HOVER_BACKGROUND_COLOR =
    new JBColor(new Color(0xCFE6EF), JBColor.LIGHT_GRAY.brighter());
  final static private JBColor HOVER_FOREGROUND_COLOR = JBColor.BLACK;

  private int rollOverRowIndex = -1;
  private int lastClickedRow = -1;

  public HighlightedTable(TableModel model) {
    super(model);

    final RollOverListener listener = new RollOverListener();
    addMouseMotionListener(listener);
    addMouseListener(listener);
  }

  @NotNull
  @Override
  public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
    final Component c = super.prepareRenderer(renderer, row, column);
    final Font font = c.getFont();
    if (font != null) {
      // Iff we have a font for this component (TableRow).
      if (lastClickedRow == row) {
        c.setFont(font.deriveFont(Font.BOLD));
        c.setForeground(getSelectionForeground());
        c.setBackground(getSelectionBackground());
      }
      else {
        c.setFont(font.deriveFont(Font.PLAIN));
        if (row == rollOverRowIndex) {
          c.setForeground(HOVER_FOREGROUND_COLOR);
          c.setBackground(HOVER_BACKGROUND_COLOR);
        }
        else {
          c.setForeground(getForeground());
          c.setBackground(getBackground());
        }
      }
    }

    return c;
  }

  private class RollOverListener extends MouseInputAdapter {
    @Override
    public void mouseExited(MouseEvent e) {
      rollOverRowIndex = -1;
      repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      final int row = rowAtPoint(e.getPoint());
      if (row != rollOverRowIndex) {
        rollOverRowIndex = row;
        repaint();
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      lastClickedRow = rowAtPoint(e.getPoint());
      repaint();
    }
  }
}
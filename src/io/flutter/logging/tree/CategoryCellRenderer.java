/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredRenderer;
import io.flutter.logging.FlutterLogColors;
import io.flutter.logging.FlutterLogEntry;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class CategoryCellRenderer extends SimpleColoredRenderer implements TableCellRenderer {

  @Override
  public final Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

    if (value instanceof FlutterLogEntry) {
      final String category = ((FlutterLogEntry)value).getCategory();
      final JLabel label = new JLabel(" " + category + " ");

      label.setBackground(FlutterLogColors.forCategory(category));
      label.setForeground(JBColor.background());
      label.setOpaque(true);
      panel.add(Box.createHorizontalGlue());
      panel.add(label);
      panel.add(Box.createHorizontalStrut(8));
    }

    clear();
    setPaintFocusBorder(hasFocus && table.getCellSelectionEnabled());
    acquireState(table, isSelected, hasFocus, row, col);
    getCellState().updateRenderer(this);

    if (isSelected) {
      panel.setBackground(table.getSelectionBackground());
    }

    return panel;
  }
}

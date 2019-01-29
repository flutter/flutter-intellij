/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.flutter.logging.FlutterLogColors;
import io.flutter.logging.FlutterLogEntry;
import io.flutter.logging.FlutterLogTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public class CategoryCellRenderer extends SimpleColoredRenderer implements TableCellRenderer {
  @NotNull
  private final FlutterLogTree.EntryModel entryModel;

  public CategoryCellRenderer(@NotNull FlutterLogTree.EntryModel entryModel){
    this.entryModel = entryModel;
  }

  @Override
  public final Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setBackground(JBColor.background());

    if (value instanceof FlutterLogEntry) {
      final FlutterLogEntry entry = (FlutterLogEntry)value;
      final String category = entry.getCategory();
      final JLabel label = new JLabel(" " + category + " ");
      label.setBackground(FlutterLogColors.forCategory(category));
      label.setForeground(JBColor.background());
      label.setOpaque(true);
      panel.add(Box.createHorizontalGlue());
      panel.add(label);
      panel.add(Box.createHorizontalStrut(8));

      final SimpleTextAttributes style = entryModel.style(entry, STYLE_PLAIN);
      if (style.getBgColor() != null) {
        setBackground(style.getBgColor());
        panel.setBackground(style.getBgColor());
      }

      AbstractEntryCellRender.updateEntryBorder(panel, entry, row);
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

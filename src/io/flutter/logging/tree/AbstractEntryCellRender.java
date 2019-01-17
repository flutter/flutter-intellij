/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import io.flutter.logging.FlutterLogEntry;
import io.flutter.logging.FlutterLogTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public abstract class AbstractEntryCellRender extends ColoredTableCellRenderer {

  private final Border RELOAD_LINE =
    new CompoundBorder(BorderFactory.createEmptyBorder(0, -1, -1, -1), BorderFactory.createDashedBorder(
      JBColor.ORANGE, 5, 5));
  private final Border RESTART_LINE =
    new CompoundBorder(BorderFactory.createEmptyBorder(0, -1, -1, -1), BorderFactory.createDashedBorder(JBColor.GREEN, 5, 5));

  @NotNull
  protected final FlutterLogTree.EntryModel entryModel;

  AbstractEntryCellRender(@NotNull FlutterLogTree.EntryModel entryModel) {
    this.entryModel = entryModel;
  }

  @Override
  protected final void customizeCellRenderer(JTable table,
                                             @Nullable Object value,
                                             boolean selected,
                                             boolean hasFocus,
                                             int row,
                                             int column) {
    if (value instanceof FlutterLogEntry) {
      final FlutterLogEntry entry = (FlutterLogEntry)value;
      render(entry);
      if (row > 0) {
        if (entry.getKind() == FlutterLogEntry.Kind.RELOAD) {
          setBorder(RELOAD_LINE);
        }
        else if (entry.getKind() == FlutterLogEntry.Kind.RESTART) {
          setBorder(RESTART_LINE);
        }
      }
    }
  }

  @Override
  public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
    // Prevent cell borders on selected cells.
    super.acquireState(table, isSelected, false, row, column);
  }

  void appendStyled(FlutterLogEntry entry, String text) {
    final SimpleTextAttributes style = entryModel.style(entry, STYLE_PLAIN);
    if (style.getBgColor() != null) {
      setBackground(style.getBgColor());
    }
    append(text, style);
  }

  abstract void render(FlutterLogEntry entry);
}

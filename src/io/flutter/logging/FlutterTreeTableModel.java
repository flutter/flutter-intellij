/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.ui.treeStructure.treetable.TreeTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

class FlutterTreeTableModel implements TableModel {
  @NotNull
  private final TableModel model;

  FlutterTreeTableModel(@NotNull TreeTable treeTable) {
    model = treeTable.getModel();
  }

  @Override
  public int getRowCount() {
    return model.getRowCount();
  }

  @Override
  public int getColumnCount() {
    return ColumnIndex.values().length;
  }

  @Override
  public String getColumnName(int columnIndex) {
    return model.getColumnName(columnIndex);
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return model.getColumnClass(columnIndex);
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return model.isCellEditable(rowIndex, columnIndex);
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final Object obj = model.getValueAt(rowIndex, columnIndex);
    final ColumnIndex index = ColumnIndex.forIndex(columnIndex);

    if (index != ColumnIndex.INVALID && obj instanceof FlutterLogEntry) {
      return index.toValue((FlutterLogEntry)obj);
    }
    return obj;
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    model.setValueAt(aValue, rowIndex, columnIndex);
  }

  @Override
  public void addTableModelListener(TableModelListener l) {
    model.addTableModelListener(l);
  }

  @Override
  public void removeTableModelListener(TableModelListener l) {
    model.removeTableModelListener(l);
  }

  public enum ColumnIndex {
    INVALID(-1) {
      @Override
      public Object toValue(@NotNull FlutterLogEntry entry) {
        throw new IllegalStateException("Can't get value for `INVALID`");
      }
    },
    MESSAGE(0) {
      @Override
      public Object toValue(@NotNull FlutterLogEntry entry) {
        return entry.getMessage();
      }
    },
    CATEGORY(1) {
      @Override
      public Object toValue(@NotNull FlutterLogEntry entry) {
        return entry.getCategory();
      }
    },
    LOG_LEVEL(2) {
      @Override
      public Object toValue(@NotNull FlutterLogEntry entry) {
        return entry.getLevel();
      }
    };

    public final int index;

    ColumnIndex(int index) {
      this.index = index;
    }

    public abstract Object toValue(@NotNull FlutterLogEntry entry);

    @NotNull
    public static ColumnIndex forIndex(int index) {
      for (ColumnIndex columnIndex : values()) {
        if (columnIndex.index == index) {
          return columnIndex;
        }
      }
      return INVALID;
    }
  }
}

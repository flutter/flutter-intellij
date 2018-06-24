/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.v2;

import com.intellij.ui.table.JBTable;
import com.intellij.util.Alarm;
import io.flutter.logging.FlutterLog;
import io.flutter.logging.FlutterLogConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.regex.PatternSyntaxException;

public class FlutterLogTree extends JBTable {
  @NotNull
  private final FlutterLog flutterLog;
  @NotNull
  private final FlutterTableModel flutterTableMode;
  @NotNull
  private final TableRowSorter<FlutterTableModel> sorter;
  @NotNull
  private final Alarm uiThreadAlarm;

  public FlutterLogTree(@NotNull FlutterLog flutterLog) {
    this.flutterLog = flutterLog;

    settingUi();

    flutterTableMode = new FlutterTableModel(flutterLog);
    setModel(flutterTableMode);
    sorter = new TableRowSorter<>(flutterTableMode);
    setRowSorter(sorter);

    settingColumnWidth();

    uiThreadAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    flutterTableMode.addTableModelListener(e -> uiThreadAlarm.addRequest(this::scrollToBottom, 10));
  }

  private void settingColumnWidth() {
    fixColumnWidth(FlutterLogConstants.LogColumns.TIME, 100);
    fixColumnWidth(FlutterLogConstants.LogColumns.SEQUENCE, 50);
    fixColumnWidth(FlutterLogConstants.LogColumns.LEVEL, 70);
    fixColumnWidth(FlutterLogConstants.LogColumns.CATEGORY, 110);
  }

  private void settingUi() {
    setFillsViewportHeight(true);
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setAutoscrolls(true);
    setShowHorizontalLines(false);
    setShowVerticalLines(false);
    setShowGrid(false);
  }

  // FIXME(quangson91): temporary implement scroll to the bottom.
  private void scrollToBottom() {
    final Container parent = getParent().getParent();
    final JScrollPane scrollPane;
    if (parent instanceof JScrollPane) {
      scrollPane = (JScrollPane)parent;
    }
    else {
      return;
    }
    final JScrollBar vertical = scrollPane.getVerticalScrollBar();
    vertical.setValue(vertical.getMaximum());
  }

  private void fixColumnWidth(@NotNull String columnName, int width) {
    final TableColumn column;
    try {
      column = getColumn(columnName);
    }
    catch (Exception ignored) {
      return;
    }
    column.setMinWidth(width);
    column.setMaxWidth(width);
    column.setPreferredWidth(width);
  }

  public void filter(@Nullable String regex) {
    final String nonNullRegex = regex == null ? "(?s).*" : regex;
    final RowFilter<FlutterTableModel, Object> rowFilter;
    //If current expression doesn't parse, don't update.
    try {
      rowFilter = RowFilter.regexFilter(regex, 3, 4);
    }
    catch (PatternSyntaxException e) {
      return;
    }
    sorter.setRowFilter(rowFilter);
  }

  @NotNull
  public FlutterTableModel getFlutterTableMode() {
    return flutterTableMode;
  }
}

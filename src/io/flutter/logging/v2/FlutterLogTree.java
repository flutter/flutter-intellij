/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.v2;

import com.intellij.ui.table.JBTable;
import io.flutter.logging.FlutterLog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterLogTree extends JBTable {
  @NotNull
  private final FlutterLog flutterLog;
  @NotNull
  private final FlutterTableModel flutterTableMode;

  public FlutterLogTree(@NotNull FlutterLog flutterLog) {
    this.flutterLog = flutterLog;

    // TODO(quangson91): Setting sorter/filtering.

    setFillsViewportHeight(true);
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setAutoscrolls(true);

    flutterTableMode = new FlutterTableModel(flutterLog);
    setModel(flutterTableMode);
  }

  @NotNull
  public FlutterTableModel getFlutterTableMode() {
    return flutterTableMode;
  }
}

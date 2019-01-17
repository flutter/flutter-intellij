/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import io.flutter.logging.FlutterLog;
import io.flutter.logging.FlutterLogEntry;
import io.flutter.logging.FlutterLogTree;
import org.jetbrains.annotations.NotNull;

public class LevelCellRenderer extends AbstractEntryCellRender {
  public LevelCellRenderer(@NotNull FlutterLogTree.EntryModel entryModel) {
    super(entryModel);
  }

  @Override
  void render(FlutterLogEntry entry) {
    final FlutterLog.Level level = FlutterLog.Level.forValue(entry.getLevel());
    final String value = level.toDisplayString();
    appendStyled(entry, value);
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import io.flutter.logging.FlutterLogEntry;
import io.flutter.logging.FlutterLogTree;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;

public class TimeCellRenderer extends AbstractEntryCellRender {

  public static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

  public TimeCellRenderer(@NotNull FlutterLogTree.EntryModel entryModel) {
    super(entryModel);
  }

  @Override
  void render(FlutterLogEntry entry) {
    appendStyled(entry, TIMESTAMP_FORMAT.format(entry.getTimestamp()));
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.v2;

import com.intellij.openapi.Disposable;
import com.intellij.util.Alarm;
import io.flutter.logging.FlutterLog;
import io.flutter.logging.FlutterLogConstants;
import io.flutter.logging.FlutterLogEntry;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.DefaultTableModel;

public class FlutterTableModel extends DefaultTableModel implements Disposable, FlutterLog.Listener {

  @NotNull
  private final FlutterLog flutterLog;
  @NotNull
  private final Alarm uiThreadAlarm;

  public FlutterTableModel(@NotNull FlutterLog flutterLog) {
    super(FlutterLogConstants.LogColumns.ALL_COLUMNS, 0);
    this.flutterLog = flutterLog;
    flutterLog.addListener(this, this);
    uiThreadAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  }

  @Override
  public void dispose() {
    flutterLog.removeListener(this);
  }

  @Override
  public void onEvent(@NotNull FlutterLogEntry entry) {
    // TODO(quangson91): optimize only fire new row insersted ???
    uiThreadAlarm.addRequest(() -> {
      addRow(createLog(entry));
    }, 10);
  }


  @NotNull
  private Object[] createLog(@NotNull FlutterLogEntry entry) {
    return new String[]{
      "00:00:00",
      "num",
      "level",
      "category",
      entry.getMessage()
    };
  }
}

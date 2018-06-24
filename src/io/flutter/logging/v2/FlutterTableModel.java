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
import java.text.SimpleDateFormat;

public class FlutterTableModel extends DefaultTableModel implements Disposable, FlutterLog.Listener {
  @NotNull
  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

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
    uiThreadAlarm.addRequest(() -> addRow(createLog(entry)), 1);
  }

  @NotNull
  private Object[] createLog(@NotNull FlutterLogEntry entry) {
    return new String[]{
      TIMESTAMP_FORMAT.format(entry.getTimestamp()),
      Integer.toString(entry.getSequenceNumber()),
      toLogLevel(entry),
      entry.getCategory(),
      entry.getMessage()
    };
  }

  @NotNull
  private String toLogLevel(@NotNull FlutterLogEntry entry) {
    final FlutterLog.Level level = FlutterLog.Level.forValue(entry.getLevel());
    return level != null ? level.name() : Integer.toString(entry.getLevel());
  }
}

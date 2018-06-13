/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterLogEntry {

  public static final FlutterLog.Level UNDEFINED_LEVEL = FlutterLog.Level.INFO;

  private final long timestamp;
  @NotNull
  private final String category;
  private final int level;
  @NotNull
  private final String message;
  private int sequenceNumber = -1;


  public FlutterLogEntry(long timestamp, @NotNull String category, int level, @Nullable String message) {
    this.timestamp = timestamp;
    this.category = category;
    this.level = level;
    this.message = StringUtil.notNullize(message);
  }

  public FlutterLogEntry(long timestamp, @NotNull String category, @Nullable String message) {
    this(timestamp, category, UNDEFINED_LEVEL.value, message);
  }

  public long getTimestamp() {
    return timestamp;
  }

  public int getLevel() {
    return level;
  }

  @NotNull
  public String getCategory() {
    return category;
  }

  @NotNull
  public String getMessage() {
    return message;
  }

  /**
   * Return a sequence number, or -1 if unset.
   */
  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public void setSequenceNumber(int sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }
}

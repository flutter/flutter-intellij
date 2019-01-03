/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.util.text.StringUtil;
import io.flutter.logging.util.LineInfo;
import io.flutter.logging.util.StyledText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterLogEntry {

  public enum Kind {
    RELOAD,
    RESTART,
    WIDGET_ERROR_START,
    UNSPECIFIED
  }

  public static final FlutterLog.Level UNDEFINED_LEVEL = FlutterLog.Level.INFO;

  private final long timestamp;
  @NotNull
  private final String category;
  private final int level;
  @NotNull
  private final String message;
  // TODO(pq): consider making data an Instance or JsonElement
  @Nullable
  private String data;
  private int sequenceNumber = -1;

  @NotNull
  private final Kind kind;
  @NotNull private final List<StyledText> styledText;

  public FlutterLogEntry(long timestamp,
                         @NotNull String category,
                         int level,
                         @Nullable String message,
                         @NotNull Kind kind,
                         @NotNull List<StyledText> styledText) {
    this.timestamp = timestamp;
    this.category = category;
    this.level = level;
    this.message = StringUtil.notNullize(message);
    this.kind = kind;
    this.styledText = styledText;
  }

  public FlutterLogEntry(long timestamp, @NotNull LineInfo info, int level) {
    this(timestamp, info.getCategory(), level, info.getLine(), info.getKind(), info.getStyledText());
  }

  @Nullable
  public String getData() {
    return data;
  }

  public void setData(@Nullable String data) {
    this.data = data;
  }

  @NotNull
  public Kind getKind() {
    return kind;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public int getLevel() {
    return level;
  }

  @NotNull
  public String getLevelName() {
    return FlutterLog.Level.forValue(level).toDisplayString();
  }

  @NotNull
  public String getCategory() {
    return category;
  }

  @NotNull
  public String getMessage() {
    return message;
  }

  @NotNull
  public List<StyledText> getStyledText() {
    return styledText;
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

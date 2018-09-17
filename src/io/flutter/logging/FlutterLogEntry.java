/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.flutter.logging.FlutterLogEntryParser.TOOLS_CATEGORY;

public class FlutterLogEntry {

  public enum Kind {
    RELOAD,
    RESTART,
    UNSPECIFIED
  }

  public static final FlutterLog.Level UNDEFINED_LEVEL = FlutterLog.Level.INFO;

  private final long timestamp;
  @NotNull
  private final String category;
  private final int level;
  @NotNull
  private final String message;
  private int sequenceNumber = -1;

  @NotNull
  private final Kind kind;

  public FlutterLogEntry(long timestamp, @NotNull String category, int level, @Nullable String message) {
    this.timestamp = timestamp;
    this.category = category;
    this.level = level;
    this.message = StringUtil.notNullize(message);
    this.kind = parseKind(category, this.message);
  }

  private static Kind parseKind(@NotNull String category, @NotNull String message) {
    if (category.equals(TOOLS_CATEGORY)) {
      message = message.trim();
      if (message.equals("Performing hot reload...") || message.equals("Initializing hot reload...")) {
        return Kind.RELOAD;
      }
      if (message.equals("Performing hot restart...") || message.equals("Initializing hot restart...")) {
        return Kind.RESTART;
      }
    }
    return Kind.UNSPECIFIED;
  }

  public FlutterLogEntry(long timestamp, @NotNull String category, @Nullable String message) {
    this(timestamp, category, UNDEFINED_LEVEL.value, message);
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

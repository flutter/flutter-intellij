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

  private final long timestamp;
  @NotNull
  private final String category;
  @NotNull
  private final String message;
  private int sequenceNumber = -1;

  public FlutterLogEntry(long timestamp, @NotNull String category, @Nullable String message) {
    this.timestamp = timestamp;
    this.category = category;
    this.message = StringUtil.notNullize(message);
  }

  public long getTimestamp() {
    return timestamp;
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

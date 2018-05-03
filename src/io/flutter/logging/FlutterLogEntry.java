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

  // TODO(pq): assuming we keep a timestamp, consider making it a long.
  @NotNull
  private final String timestamp;
  @NotNull
  private final String category;
  @NotNull
  private final String message;

  public FlutterLogEntry(@NotNull String timestamp, @NotNull String category, @Nullable String message) {
    this.timestamp = timestamp;
    this.category = category;
    this.message = StringUtil.notNullize(message);
  }

  @NotNull
  public String getTimestamp() {
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
}

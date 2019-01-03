/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.util;

import io.flutter.logging.FlutterLogEntry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LineInfo {

  @NotNull private final String line;
  @NotNull private final List<StyledText> styledText;
  private final FlutterLogEntry.Kind kind;
  @NotNull private final String category;

  public LineInfo(@NotNull String line, @NotNull List<StyledText> styledText, FlutterLogEntry.Kind kind, @NotNull String category) {
    this.line = line;
    this.styledText = styledText;
    this.kind = kind;
    this.category = category;
  }

  @NotNull
  public String getLine() {
    return line;
  }

  public FlutterLogEntry.Kind getKind() {
    return kind;
  }

  @NotNull
  public List<StyledText> getStyledText() {
    return styledText;
  }

  @NotNull
  public String getCategory() {
    return category;
  }
}

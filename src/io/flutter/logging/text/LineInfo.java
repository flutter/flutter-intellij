/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.text;

import com.intellij.execution.filters.Filter;
import com.intellij.ui.SimpleTextAttributes;
import io.flutter.logging.FlutterLogEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LineInfo {

  @NotNull private final String line;
  @NotNull private final List<StyledText> styledText;
  private final FlutterLogEntry.Kind kind;
  @NotNull private final String category;
  @NotNull private final List<Filter> filters;
  @Nullable private final SimpleTextAttributes inheritedStyle;

  public LineInfo(@NotNull String line,
                  @NotNull List<StyledText> styledText,
                  FlutterLogEntry.Kind kind,
                  @NotNull String category,
                  @NotNull List<Filter> filters,
                  @Nullable SimpleTextAttributes inheritedStyle) {
    this.line = line;
    this.styledText = styledText;
    this.kind = kind;
    this.category = category;
    this.filters = filters;
    this.inheritedStyle = inheritedStyle;
  }

  @NotNull
  public String getLine() {
    return line;
  }

  @NotNull
  public List<Filter> getFilters() {
    return filters;
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

  @Nullable
  public SimpleTextAttributes getInheritedStyle() {
    return inheritedStyle;
  }
}

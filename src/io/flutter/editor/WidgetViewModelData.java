/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WidgetViewModelData {
  @Nullable public final WidgetIndentGuideDescriptor descriptor;
  @Nullable public final RangeHighlighter highlighter;
  public final WidgetEditingContext context;

  public WidgetViewModelData(
    WidgetEditingContext context
  ) {
    this.highlighter = null;
    this.descriptor = null;
    this.context = context;
  }

  public WidgetViewModelData(
    WidgetIndentGuideDescriptor descriptor,
    @NotNull RangeHighlighter highlighter,
    WidgetEditingContext context
  ) {
    this.highlighter = highlighter;
    this.descriptor = descriptor;
    this.context = context;
  }

  public TextRange getMarker() {
    if (descriptor != null) {
      return descriptor.getMarker();
    }
    return null;
  }
}

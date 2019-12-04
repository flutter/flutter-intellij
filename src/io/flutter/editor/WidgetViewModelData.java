/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

public class WidgetViewModelData {
  public final WidgetEditingContext context;

  public WidgetViewModelData(
    WidgetEditingContext context
  ) {
    this.context = context;
  }
}

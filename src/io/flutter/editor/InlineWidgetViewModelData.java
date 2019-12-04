/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class InlineWidgetViewModelData extends WidgetViewModelData {
  public final WidgetIndentGuideDescriptor descriptor;
  public final Document document;
  public final EditorEx editor;

  public InlineWidgetViewModelData(
    WidgetIndentGuideDescriptor descriptor,
    EditorEx editor,
    WidgetEditingContext context
  ) {
    super(context);
    this.descriptor = descriptor;
    this.document = editor.getDocument();
    this.editor = editor;
  }

  public TextRange getMarker() {
    if (descriptor != null) {
      return descriptor.getMarker();
    }
    return null;
  }
}

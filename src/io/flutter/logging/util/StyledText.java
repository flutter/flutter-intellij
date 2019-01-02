/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.util;

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StyledText {
  @NotNull private final String text;
  @Nullable private final SimpleTextAttributes style;
  @Nullable private final Object tag;

  public StyledText(@NotNull String text, @Nullable SimpleTextAttributes style) {
    this(text, style, null);
  }

  public StyledText(@NotNull String text, @Nullable SimpleTextAttributes style, @Nullable Object tag) {
    this.text = text;
    this.style = style;
    this.tag = tag;
  }

  @Nullable
  public SimpleTextAttributes getStyle() {
    return style;
  }

  @NotNull
  public String getText() {
    return text;
  }

  @Nullable
  public Object getTag() {
    return tag;
  }
}

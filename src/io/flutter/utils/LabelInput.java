/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.ui.components.labels.LinkListener;
import org.jetbrains.annotations.NotNull;

public class LabelInput {
  public String text;
  public LinkListener<String> listener;

  public LabelInput(@NotNull String text) {
    this(text, null);
  }

  public LabelInput(String text, LinkListener<String> listener) {
    this.text = text;
    this.listener = listener;
  }
}

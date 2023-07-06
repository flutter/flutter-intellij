/*
 * Copyright 2023 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ui.content.ContentManager;

import javax.swing.*;

public interface EmbeddedTab {
  void loadUrl(String url);

  void close();

  JComponent getTabComponent(ContentManager contentManager);
}

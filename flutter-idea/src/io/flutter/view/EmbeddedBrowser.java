/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ui.content.ContentManager;
import io.flutter.FlutterInitializer;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.jxbrowser.EmbeddedJxBrowser;
import io.flutter.utils.AsyncUtils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface EmbeddedBrowser {
  void resetUrl();

  void openPanel(ContentManager contentManager, String tabName, DevToolsUrl devToolsUrl, Runnable onBrowserUnavailable);

  void updatePanelToWidget(String widgetId);

  void updateColor(String newColor);

  void updateFontSize(float newFontSize);
}

/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import io.flutter.devtools.DevToolsUtils;
import org.jetbrains.annotations.NotNull;

public class WidgetPreviewUrlProvider implements BrowserUrlProvider {
  private final @NotNull String url;
  private boolean isBackgroundBright;

  public WidgetPreviewUrlProvider(@NotNull String url, boolean isBackgroundBright) {
    this.url = url;
    this.isBackgroundBright = isBackgroundBright;
  }

  @Override
  public void setWidgetId(@NotNull String widgetId) {

  }

  @Override
  public @NotNull String getBrowserUrl() {
    return url + "?theme=" + (isBackgroundBright ? "light" : "dark");
  }

  @Override
  public boolean maybeUpdateColor() {
    final boolean newIsBackgroundBright = new DevToolsUtils().getIsBackgroundBright();
    if (isBackgroundBright == newIsBackgroundBright) {
      return false;
    }
    isBackgroundBright = newIsBackgroundBright;
    return true;
  }

  @Override
  public boolean setVmServiceUri(@NotNull String vmServiceUri) {
    return false;
  }
}

/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import io.flutter.devtools.DevToolsUrl;
import org.jetbrains.annotations.NotNull;

public record DevToolsUrlProvider(@NotNull DevToolsUrl url) implements BrowserUrlProvider {
  public DevToolsUrlProvider(@NotNull DevToolsUrl url) {
    this.url = url;
  }

  @Override
  public void setWidgetId(@NotNull String widgetId) {
    url.widgetId = widgetId;
  }

  @Override
  public @NotNull String getBrowserUrl() {
    return url.getUrlString();
  }

  @Override
  public void maybeUpdateColor() {
    url.maybeUpdateColor();
  }

  @Override
  public boolean setVmServiceUri(@NotNull String newVmServiceUri) {
    if (url.vmServiceUri != null && url.vmServiceUri.equals(newVmServiceUri)) {
      return false;
    }
    url.vmServiceUri = newVmServiceUri;
    return true;
  }
}

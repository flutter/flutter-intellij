/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import org.jetbrains.annotations.NotNull;

public class SimpleUrlProvider implements BrowserUrlProvider {
  private final @NotNull String url;

  public SimpleUrlProvider(@NotNull String url) {
    this.url = url;
  }

  @Override
  public void setWidgetId(@NotNull String widgetId) {

  }

  @Override
  public @NotNull String getBrowserUrl() {
    return url;
  }

  @Override
  public void maybeUpdateColor() {

  }

  @Override
  public boolean setVmServiceUri(@NotNull String vmServiceUri) {
    return false;
  }
}

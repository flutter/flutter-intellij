/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface BrowserUrlProvider {
  String widgetId = "";

  void setWidgetId(@NotNull String widgetId);

  String getBrowserUrl();

  void maybeUpdateColor();

  boolean setVmServiceUri(@NotNull String vmServiceUri);
}

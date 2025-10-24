/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import org.jetbrains.annotations.NotNull;

/**
 * Saves a browser URL that may be editable depending on additional settings from the IDE or running applications.
 */
public interface BrowserUrlProvider {
  String widgetId = "";

  void setWidgetId(@NotNull String widgetId);

  @NotNull String getBrowserUrl();

  void maybeUpdateColor();

  boolean setVmServiceUri(@NotNull String vmServiceUri);
}

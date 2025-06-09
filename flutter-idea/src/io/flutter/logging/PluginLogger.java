/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class PluginLogger {
  public static Logger createLogger(@NotNull Class logClass) {
    java.util.logging.Logger.getLogger(logClass.getName()).addHandler(new PluginLogHandler());
    return Logger.getInstance(logClass.getName());
  }
}

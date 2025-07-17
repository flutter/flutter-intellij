/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class PluginLogger {
  private static final String LOG_FILE_NAME = "flutter.log";
  private static final FileHandler fileHandler;
  static {
    final String logPath = PathManager.getLogPath();
    try {
      fileHandler = new FileHandler(logPath + File.separatorChar + LOG_FILE_NAME, 1024 * 1024, 1);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    System.setProperty("java.util.logging.SimpleFormatter.format",
                       "%1$tF %1$tT %3$s [%4$-7s] %5$s %6$s %n");
    fileHandler.setFormatter(new SimpleFormatter());
  }

  public static @NotNull Logger createLogger(@NotNull Class<?> logClass) {
    java.util.logging.Logger.getLogger(logClass.getName()).addHandler(fileHandler);
    return Logger.getInstance(logClass.getName());
  }
}

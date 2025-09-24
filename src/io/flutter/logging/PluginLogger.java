/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class PluginLogger {
  private static final String LOG_FILE_NAME = "flutter.log";

  // This handler specifies the logging format and location.
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

  // Add the handler to the root logger so that all classes within `io.flutter` log to the file correctly. We can also update the log level
  // of all classes at once by changing the root logger level.
  private static final java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("io.flutter");

  static {
    rootLogger.addHandler(fileHandler);
    if (ApplicationManager.getApplication() != null) {
      updateLogLevel();
      FlutterSettings.getInstance().addListener(PluginLogger::updateLogLevel);
    }
  }

  private static void updateLogLevel() {
    final Logger rootLoggerInstance = Logger.getInstance("io.flutter");
    rootLoggerInstance.setLevel(FlutterSettings.getInstance().isVerboseLogging() ? LogLevel.ALL : LogLevel.INFO);
  }

  public static @NotNull Logger createLogger(@NotNull Class<?> logClass) {
    return Logger.getInstance(logClass.getName());
  }
}

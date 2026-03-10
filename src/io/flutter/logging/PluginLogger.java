/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.SimpleFormatter;

public class PluginLogger {
  public static final String LOG_FILE_NAME = "dash.log";

  private static final String FLUTTER_ROOT_LOGGER_NAME = "io.flutter";
  private static final int MAX_LOG_SIZE = 10 * 1024 * 1024;
  private static final int MAX_LOG_FILES = 5;
  private static final String LOG_FORMAT_STRING = "%1$tF %1$tT %3$s [%4$-7s] %5$s %6$s %n";

  // Add the handler to the root logger so that all classes within `io.flutter`
  // log to the file correctly. We can also update the log level
  // of all classes at once by changing the root logger level.
  private static final java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger(FLUTTER_ROOT_LOGGER_NAME);
  private static final java.util.logging.Logger dartLogger = java.util.logging.Logger
    .getLogger("com.jetbrains.lang.dart");

  static {
    final String logPath = PathManager.getLogPath();
    String fullPath = logPath + File.separatorChar + LOG_FILE_NAME;

    synchronized (LogManager.getLogManager()) {
      // Try to find an existing FileHandler on either logger
      FileHandler existingHandler = null;

      if (rootLogger != null) {
        Handler[] rootHandlers = rootLogger.getHandlers();
        if (rootHandlers != null) {
          for (Handler handler : rootHandlers) {
            if (handler instanceof FileHandler) {
              existingHandler = (FileHandler)handler;
              break;
            }
          }
        }
      }

      if (existingHandler != null) {
        // Another plugin initialized first; reuse its handler
        if (rootLogger != null) {
          boolean hasHandler = false;
          if (rootLogger.getHandlers() != null) {
            for (Handler h : rootLogger.getHandlers()) {
              if (h == existingHandler) {
                hasHandler = true;
                break;
              }
            }
          }
          if (!hasHandler) {
            rootLogger.addHandler(existingHandler);
          }
        }
      }
      else {
        // We are the first plugin to initialize; create the handler
        try {
          FileHandler newHandler = new FileHandler(fullPath, MAX_LOG_SIZE, MAX_LOG_FILES, true);
          System.setProperty("java.util.logging.SimpleFormatter.format", LOG_FORMAT_STRING);
          newHandler.setFormatter(new SimpleFormatter());

          // Attach to logger so the next plugin finds it
          if (rootLogger != null) {
            rootLogger.addHandler(newHandler);
          }
        }
        catch (IOException | SecurityException e) {
          java.util.logging.Logger.getLogger(PluginLogger.class.getName())
            .log(Level.WARNING, "Failed to initialize plugin log file handler", e);
        }
      }
    }
  }

  public static void updateLogLevel() {
    final Logger rootLoggerInstance = Logger.getInstance(FLUTTER_ROOT_LOGGER_NAME);
    // Workaround for https://github.com/flutter/flutter-intellij/issues/8631
    if (rootLoggerInstance.getClass().getName().equals("com.haulmont.jmixstudio.logger.JmixLoggerWrapper")) {
      return;
    }
    try {
      rootLoggerInstance.setLevel(FlutterSettings.getInstance().isVerboseLogging() ? LogLevel.ALL : LogLevel.INFO);
    }
    catch (Throwable e) {
      // This can happen if the logger is wrapped by a 3rd party plugin that doesn't
      // correctly implement setLevel.
      // See https://github.com/flutter/flutter-intellij/issues/8631
      Logger.getInstance(PluginLogger.class).info("Failed to set log level");
    }
  }

  public static @NotNull Logger createLogger(@NotNull Class<?> logClass) {
    return Logger.getInstance(logClass.getName());
  }
}

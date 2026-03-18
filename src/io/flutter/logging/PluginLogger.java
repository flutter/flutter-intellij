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
import org.jspecify.annotations.Nullable;

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

  private static boolean isInitialized = false;

  public static void initLogger() {
    if (isInitialized) return;

    final String logPath = PathManager.getLogPath();
    String fullPath = logPath + File.separatorChar + LOG_FILE_NAME;

    synchronized (LogManager.getLogManager()) {
      isInitialized = true;
      // Try to find an existing FileHandler on either logger
      FileHandler existingHandler = getExistingFileHandler(rootLogger);
      if (existingHandler == null) {
        existingHandler = getExistingFileHandler(dartLogger);
      }

      if (existingHandler != null) {
        // Another plugin initialized first; reuse its handler
        ensureHandlerSet(rootLogger, existingHandler);
      }
      else {
        // We are the first plugin to initialize; create the handler
        try {
          FileHandler newHandler = new FileHandler(fullPath, MAX_LOG_SIZE, MAX_LOG_FILES, true);
          newHandler.setFormatter(createLogFormatter());

          // Attach to logger so the next plugin finds it
          ensureHandlerSet(rootLogger, newHandler);
        }
        catch (IOException | SecurityException e) {
          java.util.logging.Logger.getLogger(PluginLogger.class.getName())
            .log(Level.WARNING, "Failed to initialize plugin log file handler", e);
        }
      }
    }
  }

  private static void ensureHandlerSet(java.util.logging.Logger logger, FileHandler handler) {
    if (logger != null) {
      Handler[] handlers = logger.getHandlers();
      boolean hasHandler = handlers != null && java.util.Arrays.stream(handlers).anyMatch(h -> h == handler);
      if (!hasHandler) {
        logger.addHandler(handler);
      }
    }
  }

  private static @Nullable FileHandler getExistingFileHandler(java.util.logging.Logger logger) {
    if (logger == null) return null;
    Handler[] handlers = logger.getHandlers();
    if (handlers != null) {
      for (Handler handler : handlers) {
        if (handler instanceof FileHandler) {
          return (FileHandler)handler;
        }
      }
    }
    return null;
  }

  private static @NotNull java.util.logging.Formatter createLogFormatter() {
    return new java.util.logging.Formatter() {
      @Override
      public String format(java.util.logging.LogRecord record) {
        return String.format(LOG_FORMAT_STRING,
          new java.util.Date(record.getMillis()),
          null, // Not using source name in the format string
          record.getLoggerName(),
          record.getLevel().getLocalizedName(),
          super.formatMessage(record),
          (record.getThrown() != null ? "\n" + com.intellij.openapi.util.text.StringUtil.getThrowableText(record.getThrown()) : "")
        );
      }
    };
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

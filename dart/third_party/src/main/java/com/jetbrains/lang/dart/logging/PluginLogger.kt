/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.logging

import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.LogManager
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import com.intellij.openapi.diagnostic.Logger as IJLogger

object PluginLogger {
  private const val LOG_FILE_NAME = "dash.log"
  private const val FLUTTER_LOGGER_NAME = "io.flutter"
  private const val LOG_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
  private const val LOG_FILE_COUNT = 5
  private const val LOG_FORMAT = "%1\$tF %1\$tT %3\$s [%4$-7s] %5\$s %6\$s %n"

  private val rootLogger: Logger = Logger.getLogger("com.jetbrains.lang.dart")

  init {
    val logPath = PathManager.getLogPath()
    val fullPath = logPath + File.separatorChar + LOG_FILE_NAME

    synchronized(LogManager.getLogManager()) {
      val flutterLogger = Logger.getLogger(FLUTTER_LOGGER_NAME)

      // Check if either logger already has a FileHandler for dash.log
      val existingHandler = rootLogger.handlers.filterIsInstance<FileHandler>().firstOrNull()
        ?: flutterLogger.handlers.filterIsInstance<FileHandler>().firstOrNull()

      if (existingHandler != null) {
        // Another plugin initialized first; reuse its handler
        if (!rootLogger.handlers.contains(existingHandler)) {
          rootLogger.addHandler(existingHandler)
        }
      } else {
        // We are the first plugin to initialize; create the handler
        try {
          val newHandler = FileHandler(fullPath, LOG_FILE_SIZE_BYTES, LOG_FILE_COUNT, true)
          newHandler.formatter = PluginLogFormatter()

          // Attach to the dart logger so it can be found
          rootLogger.addHandler(newHandler)
        } catch (e: IOException) {
          // TODO(helin24): Consider adding a backup logging method if this fails at any point.
          IJLogger.getInstance(PluginLogger::class.java).error("Failed to initialize plugin log file handler", e)
        } catch (e: SecurityException) {
          IJLogger.getInstance(PluginLogger::class.java).error("Failed to initialize plugin log file handler", e)
        }
      }
    }
  }

  fun createLogger(logClass: Class<*>): IJLogger {
    return IJLogger.getInstance(logClass.getName())
  }

  private class PluginLogFormatter : java.util.logging.Formatter() {
    override fun format(record: java.util.logging.LogRecord): String {
      return String.format(
        LOG_FORMAT,
        java.util.Date(record.millis),
        null,
        record.loggerName,
        record.level.localizedName,
        formatMessage(record),
        if (record.thrown != null) "\n" + record.thrown.stackTraceToString() else ""
      )
    }
  }
}

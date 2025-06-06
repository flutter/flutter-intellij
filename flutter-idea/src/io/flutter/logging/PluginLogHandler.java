/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.application.PathManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class PluginLogHandler extends Handler {
  private static final String LOG_FILE_NAME = "flutter.log";
  public PluginLogHandler() {
  }

  @Override
  public void publish(LogRecord record) {
    // Write to a file
    final String logPath = PathManager.getLogPath();
    System.out.println("Log path: " + logPath);
    File logFile = new File(logPath + File.separatorChar + LOG_FILE_NAME);

    try {
      if (!logFile.exists()) {
        logFile.createNewFile(); // Create the file if it doesn't exist
        System.out.println("Log file created: " + logFile.getAbsolutePath());
      }
      FileWriter writer = new FileWriter(logFile, true); // Append to the file

      String message = record.getInstant().toString() + " " + record.getLevel() + ": " + record.getMessage() + System.lineSeparator();
      writer.write(message); // Write the log message
      writer.close(); // Close the writer
    } catch (IOException e) {
      System.err.println("Error creating or writing to the log file: " + e.getMessage());
    }
  }

  @Override
  public void flush() {
    System.out.println("LOG FLUSH: ");
  }

  @Override
  public void close() throws SecurityException {
    System.out.println("in close of logging");
  }
}

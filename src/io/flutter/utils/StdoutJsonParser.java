/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to process regular text output intermixed with newline-delimited JSON.
 */
public class StdoutJsonParser {
  private final StringBuilder buffer = new StringBuilder();
  private final List<String> lines = new ArrayList<>();

  /**
   * Write new output to this [StdoutJsonParser].
   */
  public void appendOutput(String output) {
    buffer.append(output);

    while (buffer.length() > 0) {
      if (buffer.length() >= 2 && buffer.charAt(0) == '[' && buffer.charAt(1) == '{') {
        final int endIndex = buffer.indexOf("}]");
        if (endIndex != -1) {
          String line = buffer.substring(0, endIndex + 2);
          buffer.delete(0, endIndex + 2);
          if (buffer.length() > 0 && buffer.charAt(0) == '\n') {
            line += "\n";
            buffer.delete(0, 1);
          }
          lines.add(line);
        }
        else {
          // Wait for a json terminator.
          break;
        }
      }
      else if (buffer.indexOf("\n") != -1) {
        final int endIndex = buffer.indexOf("\n");
        final String line = buffer.substring(0, endIndex + 1);
        buffer.delete(0, endIndex + 1);
        lines.add(line);
      }
      else {
        break;
      }
    }
  }

  /**
   * Flush any written but un-consumed output to [getAvailableLines].
   */
  public void flush() {
    if (buffer.length() > 0) {
      lines.add(buffer.toString());
      buffer.setLength(0);
    }
  }

  /**
   * Read any lines available from the processed output.
   */
  public List<String> getAvailableLines() {
    final List<String> copy = new ArrayList<>(lines);
    lines.clear();
    return copy;
  }
}

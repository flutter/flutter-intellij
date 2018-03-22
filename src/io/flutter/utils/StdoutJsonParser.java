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
 *
 * JSON lines starting with [{ are never split into multiple lines even if they
 * are emitted over the course of multiple calls to appendOutput. Regular lines
 * on the other hand are emitted immediately so users do not have to wait for
 * debug output.
 */
public class StdoutJsonParser {
  private final StringBuilder buffer = new StringBuilder();
  private boolean bufferIsJson = false;
  private final List<String> lines = new ArrayList<>();

  /**
   * Write new output to this [StdoutJsonParser].
   */
  public void appendOutput(String output) {
    for (int i = 0; i < output.length(); ++i) {
      final char c = output.charAt(i);
      buffer.append(c);
      if (!bufferIsJson && buffer.length() == 2 && buffer.charAt(0) == '[' && c == '{') {
        bufferIsJson = true;
      }
      if (c == '\n') {
        flushLine();
      }
    }

    // Eagerly flush if we are not within JSON so regular log text is written
    // as soon as possible.
    if (!bufferIsJson) {
      flushLine();
    }
  }

  private void flushLine() {
    if (buffer.length() > 0) {
      lines.add(buffer.toString());
      buffer.setLength(0);
    }
    bufferIsJson = false;
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

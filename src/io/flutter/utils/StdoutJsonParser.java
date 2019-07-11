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
 * <p>
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
  public void appendOutput(String string) {
    for (int i = 0; i < string.length(); ++i) {
      final char c = string.charAt(i);
      buffer.append(c);

      if (!bufferIsJson && buffer.length() == 2 && buffer.charAt(0) == '[' && c == '{') {
        bufferIsJson = true;
      }
      else if (bufferIsJson && c == ']' && possiblyTerminatesJson(buffer, string, i)) {
        flushLine();
      }

      if (c == '\n') {
        flushLine();
      }
    }

    // Eagerly flush if we are not within JSON so regular log text is written as soon as possible.
    if (!bufferIsJson) {
      flushLine();
    }
    else if (buffer.toString().endsWith("}]")) {
      flushLine();
    }
  }

  private boolean possiblyTerminatesJson(StringBuilder output, String input, int inputIndex) {
    // This is an approximate approach to look for json message terminations inside of strings -
    // where the normally terminating eol gets separated from the json.

    if (output.length() < 2 || inputIndex + 1 >= input.length()) {
      return false;
    }

    // Look for '}', ']', and a letter
    final char prev = output.charAt(output.length() - 2);
    final char current = output.charAt(output.length() - 1);
    final char next = input.charAt(inputIndex + 1);

    return prev == '}' && current == ']' && Character.isAlphabetic(next);
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

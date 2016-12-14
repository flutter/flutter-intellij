/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fold lines like
 * '/Users/.../projects/flutter/flutter/bin/flutter --no-color packages get'.
 */
public class FlutterConsoleFolding extends ConsoleFolding {
  private static final String marker = File.separator + "flutter --no-color ";

  private boolean isFolding = false;

  // CoreSimulatorBridge: Requesting launch of ... with options: {
  private static final Pattern iosPattern = Pattern.compile("^\\w+: .* \\{$");

  @Override
  public boolean shouldFoldLine(String line) {
    if (line.contains(marker)) {
      isFolding = false;
      return true;
    }

    if (isFolding && line.startsWith(("\t"))) {
      return true;
    }

    if (iosPattern.matcher(line).matches()) {
      isFolding = true;
      return false;
    }

    isFolding = false;

    return false;
  }

  @Nullable
  @Override
  public String getPlaceholderText(List<String> lines) {
    final String fullText = StringUtil.join(lines, "\n");
    final int index = fullText.indexOf(marker);
    if (index == -1) {
      return " ... }";
    }
    else {
      return "flutter " + fullText.substring(index + marker.length());
    }
  }
}

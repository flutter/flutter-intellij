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

  //     [x86_64] libnetcore-856.20.4
  // 0   libsystem_network.dylib             0x0000000111918682 __nw_create_backtrace_string + 123
  // 1   libnetwork.dylib                    0x0000000111ab2932 nw_socket_add_input_handler + 3100
  private static final String iosCrashFormat1 = "\t        [";

  // (
  //    0   Foundation                          0x0000000102c3697d __destroyPortContext + 283
  //    1   CoreFoundation                      0x0000000105002370 ____CFMachPortChecker_block_invoke + 160
  //    11  libdyld.dylib                       0x00000001073ac68d start + 1
  // )
  private static final String iosCrashFormat2 = "\t(";

  @Override
  public boolean shouldFoldLine(String line) {
    if (line.contains(marker)) {
      isFolding = false;
      return true;
    }

    if (iosPattern.matcher(line).matches() || line.startsWith(iosCrashFormat1)) {
      isFolding = true;
      return false;
    }

    if (line.equals(iosCrashFormat2)) {
      isFolding = true;
      return true;
    }

    if (isFolding && line.startsWith(("\t"))) {
      return true;
    }
    else {
      isFolding = false;
      return false;
    }
  }

  @Nullable
  @Override
  public String getPlaceholderText(List<String> lines) {
    final String fullText = StringUtil.join(lines, "\n");
    final int index = fullText.indexOf(marker);
    if (index == -1) {
      final String trimmed = fullText.trim();

      if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
        return " ( ... )";
      }
      else if (lines.stream().anyMatch((s) -> s.endsWith("}"))) {
        return " ... }";
      }
      else {
        return " ...";
      }
    }
    else {
      return "flutter " + fullText.substring(index + marker.length());
    }
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterConsoleFolding extends ConsoleFolding {
  @Override
  public boolean shouldFoldLine(String line) {
    if (!line.contains("flutter run")) return false;

    try {
      FlutterSdk sdk = FlutterSdk.getGlobalFlutterSdk();
      if (sdk == null) return false;
      final String flutterPath = FlutterSdkUtil.pathToFlutterTool(sdk.getHomePath());
      return line.startsWith(flutterPath + " run");
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  @Nullable
  @Override
  public String getPlaceholderText(List<String> lines) {
    // /Users/.../flutter/bin/flutter run --start-paused --debug-port 50354

    String fullText = StringUtil.join(lines, "\n");

    final CommandLineTokenizer tok = new CommandLineTokenizer(fullText);
    if (!tok.hasMoreTokens()) return fullText;

    final String filePath = tok.nextToken();

    final StringBuilder builder = new StringBuilder();
    builder.append("flutter");

    while (tok.hasMoreTokens()) {
      String token = tok.nextToken();

      // strip off --start-paused
      if (token.equals("--start-paused")) continue;

      // strip off --debug-port 50354
      if (token.equals("--debug-port")) {
        if (tok.hasMoreTokens()) tok.nextToken();
        continue;
      }

      builder.append(" ").append(token);
    }

    return builder.toString();
  }
}

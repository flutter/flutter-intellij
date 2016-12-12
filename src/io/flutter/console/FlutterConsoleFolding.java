/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Fold lines like
 * '/Users/.../projects/flutter/flutter/bin/flutter --no-color packages get'.
 */
public class FlutterConsoleFolding extends ConsoleFolding {
  private static final String marker = File.separator + "flutter --no-color ";

  @Override
  public boolean shouldFoldLine(String line) {
    if (!line.contains(marker)) return false;

    try {
      final FlutterSdk sdk = FlutterSdk.getGlobalFlutterSdk();
      if (sdk == null) return false;
      final String flutterPath = FlutterSdkUtil.pathToFlutterTool(sdk.getHomePath());
      return line.startsWith(flutterPath);
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  @Nullable
  @Override
  public String getPlaceholderText(List<String> lines) {
    final String fullText = StringUtil.join(lines, "\n");
    int index = fullText.indexOf(marker);
    if (index == -1) {
      return fullText;
    }
    else {
      return "flutter " + fullText.substring(index + marker.length());
    }
  }
}

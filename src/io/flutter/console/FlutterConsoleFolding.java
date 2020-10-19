/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.FlutterConstants;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fold lines like '/Users/.../projects/flutter/flutter/bin/flutter --no-color packages get'.
 */
public class FlutterConsoleFolding extends ConsoleFolding {
  private static final String flutterMarker =
    FlutterConstants.INDEPENDENT_PATH_SEPARATOR + FlutterSdkUtil.flutterScriptName() + " --no-color ";

  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    if (line.contains(flutterMarker)) {
      return line.indexOf(' ') > line.indexOf(flutterMarker);
    }

    return false;
  }

  @Override
  public boolean shouldBeAttachedToThePreviousLine() {
    // This ensures that we don't get appended to the previous (likely unrelated) line.
    return false;
  }

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    final String fullText = StringUtil.join(lines, "\n");
    final int index = fullText.indexOf(flutterMarker);
    if (index != -1) {
      String results = "flutter " + fullText.substring(index + flutterMarker.length());
      results = results.replace("--machine ", "");
      results = results.replace("--start-paused ", "");
      return results;
    }
    else {
      return fullText.trim();
    }
  }
}

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
 * Fold lines like '/Users/.../projects/flutter/flutter/bin/flutter packages get', and their
 * Windows equivalents, such as 'C:\flutter\bin\flutter.bat packages get'.
 */
public class FlutterConsoleFolding extends ConsoleFolding {
  private static final String flutterMarker =
    FlutterConstants.INDEPENDENT_PATH_SEPARATOR + FlutterSdkUtil.flutterScriptName() + " ";

  private static final String windowsFlutterMarker = "\\" + FlutterSdkUtil.flutterScriptName() + " ";

  /**
   * Returns the index of the earliest Flutter marker in the text, using either path separator, or -1 if there is none.
   */
  private static int indexOfMarker(@NotNull String text) {
    final int index = text.indexOf(flutterMarker);
    final int windowsIndex = text.indexOf(windowsFlutterMarker);
    if (index == -1) {
      return windowsIndex;
    }
    if (windowsIndex == -1) {
      return index;
    }
    return Math.min(index, windowsIndex);
  }

  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    final int index = indexOfMarker(line);
    return index != -1 && line.indexOf(' ') > index;
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
    final int index = indexOfMarker(fullText);
    if (index != -1) {
      // Both markers have the same length, so the arguments start right after it.
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
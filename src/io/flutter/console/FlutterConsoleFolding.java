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

  /**
   * Returns true for text that begins with an absolute path: a Unix path ('/...'), a UNC path ('\\server\share'),
   * a Windows drive path ('C:\...' or 'C:/...'), or a quoted version of one of these. Android logcat lines such as
   * 'I/flutter (12345): message' do not begin with an absolute path.
   */
  private static boolean isAbsolutePath(@NotNull String line) {
    if (line.isEmpty()) {
      return false;
    }
    final int start = line.charAt(0) == '"' ? 1 : 0;
    if (line.startsWith("/", start) || line.startsWith("\\\\", start)) {
      return true;
    }
    if (line.length() - start < 3) {
      return false;
    }
    final char drive = line.charAt(start);
    return ((drive >= 'a' && drive <= 'z') || (drive >= 'A' && drive <= 'Z'))
      && line.charAt(start + 1) == ':'
      && (line.charAt(start + 2) == '\\' || line.charAt(start + 2) == '/');
  }

  /**
   * Folds lines that invoke the Flutter executable, such as '/Users/.../flutter run' (or the Windows equivalent).
   * Android log lines such as 'I/flutter (12345): message' contain '/flutter ' but do not begin with an absolute
   * path, so they are not folded.
   */
  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    final int index = indexOfMarker(line);
    return index != -1 && isAbsolutePath(line) && line.indexOf(' ') > index;
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
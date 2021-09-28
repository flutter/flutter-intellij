/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterConsoleExceptionFolding extends ConsoleFolding {
  private static final String EXCEPTION_PREFIX = "=======";
  private boolean foldLines = false;
  private boolean foldingInProgress = false;

  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    final FlutterSettings settings = FlutterSettings.getInstance();
    if (line.startsWith(EXCEPTION_PREFIX) && settings.isShowStructuredErrors() && !settings.isIncludeAllStackTraces()) {
      foldingInProgress = true;
      foldLines = !foldLines;
      return true;
    }
    return foldLines;
  }

  @Override
  public boolean shouldBeAttachedToThePreviousLine() {
    return false;
  }

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    if (foldingInProgress) {
      foldingInProgress = false;
      return lines.size() == 0 ? null : lines.get(0); // Newlines are removed, so we can only show one line.
    }
    return null;
  }
}

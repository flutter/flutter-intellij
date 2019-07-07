/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Support custom folding on Flutter run and debug consoles.
 *
 * Currently this handles folding related to Flutter.Error events.
 */
public class FlutterConsoleLogFolding extends ConsoleFolding {
  private String foldIndent;

  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    if (isFolding() && !line.startsWith(foldIndent + "  ")) {
      foldIndent = null;
    }

    boolean firstLine = false;

    if (line.endsWith(" <show more>")) {
      firstLine = true;

      final String rest = StringUtil.trimLeading(line);
      foldIndent = StringUtil.repeat(" ", line.length() - rest.length());
    }

    return isFolding() && !firstLine;
  }

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    return " ...";
  }

  private boolean isFolding() {
    return foldIndent != null;
  }
}

/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Support custom folding on Flutter run and debug consoles.
 * <p>
 * Currently this handles folding related to Flutter.Error events.
 */
public class FlutterConsoleLogFolding extends ConsoleFolding {
  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    return line.startsWith("...  ");
  }

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    return " <" + lines.size() + " children>"; // todo: plural
  }
}

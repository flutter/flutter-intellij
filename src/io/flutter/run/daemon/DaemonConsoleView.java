/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * A console view that filters out JSON messages sent in --machine mode.
 */
public class DaemonConsoleView extends ConsoleViewImpl {
  // TODO(skybrian) add UI to to set this to help people diagnose issues.
  // https://github.com/flutter/flutter-intellij/issues/976
  private static final boolean VERBOSE = false;

  public DaemonConsoleView(Project project, GlobalSearchScope searchScope) {
    super(project, searchScope, true, false);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (VERBOSE) {
      super.print(text, contentType);
      return;
    }

    final String trimmed = text.trim();

    if (trimmed.startsWith("[{") && trimmed.endsWith("}]")) {
      return;
    }

    if (trimmed.startsWith("Observatory listening on http") && !trimmed.contains("\n")) {
      return;
    }

    if (trimmed.startsWith("Diagnostic server listening on http") && !trimmed.contains("\n")) {
      return;
    }

    super.print(text, contentType);
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

/**
 * A console view that filters out JSON messages sent in --machine mode.
 */
public class DaemonConsoleView extends ConsoleViewImpl {
  private static final Logger LOG = Logger.getInstance(DaemonConsoleView.class);

  public DaemonConsoleView(Project project, GlobalSearchScope searchScope) {
    super(project, searchScope, true, false);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (FlutterInitializer.isVerboseLogging()) {
      super.print(text, contentType);
      return;
    }

    final String trimmed = text.trim();

    if (trimmed.startsWith("[{") && trimmed.endsWith("}]")) {
      LOG.info(trimmed);
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

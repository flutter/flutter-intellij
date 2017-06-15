/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.SearchScopeProvider;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
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

  /**
   * Sets up a launcher to use a DaemonConsoleView.
   */
  public static void install(@NotNull CommandLineState launcher, @NotNull ExecutionEnvironment env, @NotNull VirtualFile workDir) {
    // Create our own console builder.
    // We need to filter input to this console without affecting other consoles,
    // so we cannot use a consoleFilterInputProvider.
    final GlobalSearchScope searchScope = SearchScopeProvider.createSearchScope(env.getProject(), env.getRunProfile());
    final TextConsoleBuilder builder = new TextConsoleBuilderImpl(env.getProject(), searchScope) {
      @NotNull
      @Override
      protected ConsoleView createConsole() {
        return new DaemonConsoleView(env.getProject(), searchScope);
      }
    };

    // Set up basic console filters. (More may be added later.)
    builder.addFilter(new DartRelativePathsConsoleFilter(env.getProject(), workDir.getPath()));
    builder.addFilter(new UrlFilter());
    launcher.setConsoleBuilder(builder);
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

    if (trimmed.contains("\n")) {
      super.print(text, contentType);
      return;
    }

    if (trimmed.startsWith("Observatory listening on http")) {
      return;
    }

    if (trimmed.startsWith("Diagnostic server listening on http")) {
      return;
    }

    // "flutter test" prints this when using --start-paused.
    // (We should probably change it to stop printing it in machine mode.)
    if (trimmed.startsWith("You should first set appropriate breakpoints, then resume the test in the debugger.")) {
      return;
    }

    super.print(text, contentType);
  }
}

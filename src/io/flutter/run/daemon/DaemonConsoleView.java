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
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.StdoutJsonParser;
import org.jetbrains.annotations.NotNull;

/**
 * A console view that filters out JSON messages sent in --machine mode.
 */
public class DaemonConsoleView extends ConsoleViewImpl {
  private static final Logger LOG = Logger.getInstance(DaemonConsoleView.class);

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
    launcher.setConsoleBuilder(builder);
  }

  private final StdoutJsonParser stdoutParser = new StdoutJsonParser();
  private boolean hasPrintedText;

  public DaemonConsoleView(@NotNull final Project project, @NotNull final GlobalSearchScope searchScope) {
    super(project, searchScope, true, false);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      super.print(text, contentType);
      return;
    }

    if (text.trim().startsWith("Observatory listening on http")) {
      return;
    }

    if (contentType != ConsoleViewContentType.NORMAL_OUTPUT) {
      writeAvailableLines();

      super.print(text, contentType);
    }
    else {
      stdoutParser.appendOutput(text);
      writeAvailableLines();
    }
  }

  private void writeAvailableLines() {
    for (String line : stdoutParser.getAvailableLines()) {
      if (DaemonApi.parseAndValidateDaemonEvent(line.trim()) != null) {
        if (FlutterSettings.getInstance().isVerboseLogging()) {
          LOG.info(line.trim());
        }
        return;
      }
      else {
        // We're seeing a spurious newline before some launches; this removes any single newline that occurred
        // before we've printed text.
        if (!hasPrintedText && line.equals(("\n"))) {
          continue;
        }

        hasPrintedText = true;

        super.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
      }
    }
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.ExecutionSearchScopes;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
import io.flutter.logging.PluginLogger;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.StdoutJsonParser;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * A console view that filters out JSON messages sent in --machine mode.
 */
public class DaemonConsoleView extends ConsoleViewImpl {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(DaemonConsoleView.class);

  /**
   * Sets up a launcher to use a DaemonConsoleView.
   */
  public static void install(@NotNull CommandLineState launcher, @NotNull ExecutionEnvironment env, @NotNull VirtualFile workDir) {
    // Create our own console builder.
    //
    // We need to filter input to this console without affecting other consoles, so we cannot use a consoleFilterInputProvider.
    final GlobalSearchScope searchScope = ExecutionSearchScopes.executionScope(env.getProject(), env.getRunProfile());
    final TextConsoleBuilder builder = new TextConsoleBuilderImpl(env.getProject(), searchScope) {
      @NotNull
      @Override
      protected ConsoleView createConsole() {
        return new DaemonConsoleView(env.getProject(), searchScope);
      }
    };

    // Set up basic console filters. (More may be added later.)
    // TODO(devoncarew): Do we need this filter? What about DartConsoleFilter (for package: uris)?
    builder.addFilter(new DartRelativePathsConsoleFilter(env.getProject(), workDir.getPath()));
    launcher.setConsoleBuilder(builder);
  }

  private final StdoutJsonParser stdoutParser = new StdoutJsonParser();
  private boolean hasPrintedText;

  // Whether the console was scrolled to the end before the most recent batch of output.
  // Defaults to true so that the first output auto-scrolls normally.
  private boolean wasAtScrollEnd = true;

  public DaemonConsoleView(@NotNull final Project project, @NotNull final GlobalSearchScope searchScope) {
    super(project, searchScope, true, false);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (!FlutterModuleUtils.hasFlutterModule(getProject())) {
      return;
    }
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      super.print(text, contentType);
      return;
    }

    // Capture scroll position before queuing new output so scrollToEnd() can
    // honour it once the editor flushes the pending text.
    final Editor rawEditor = getEditor();
    final EditorEx editor = rawEditor instanceof EditorEx ? (EditorEx) rawEditor : null;
    if (editor != null) {
      wasAtScrollEnd = isAtScrollEnd(editor);
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

  /**
   * Called by the platform after flushing queued text to the editor.
   * Only scroll to end when the user was already there; otherwise preserve
   * their position so the root-cause error at the top stays visible.
   */
  @Override
  public void scrollToEnd() {
    if (wasAtScrollEnd) {
      super.scrollToEnd();
    }
  }

  static boolean isAtScrollEnd(@NotNull EditorEx editor) {
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    final Rectangle visibleArea = scrollingModel.getVisibleArea();
    return isAtScrollEnd(
      scrollingModel.getVerticalScrollOffset(),
      visibleArea.height,
      editor.getContentSize().height,
      editor.getLineHeight()
    );
  }

  static boolean isAtScrollEnd(int scrollOffset, int visibleHeight, int totalHeight, int lineHeight) {
    return scrollOffset + visibleHeight >= totalHeight - lineHeight;
  }

  private void writeAvailableLines() {
    for (String line : stdoutParser.getAvailableLines()) {
      if (DaemonApi.parseAndValidateDaemonEvent(line.trim()) != null) {
        if (FlutterSettings.getInstance().isVerboseLogging()) {
          LOG.info(line.trim());
        }
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

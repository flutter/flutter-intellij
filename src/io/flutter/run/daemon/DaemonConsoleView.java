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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
  private volatile boolean wasAtScrollEnd = true;

  // Prevents scheduling redundant EDT scroll-checks during rapid output bursts.
  private final AtomicBoolean scrollCheckPending = new AtomicBoolean(false);

  // Running count of the characters written to the underlying console document. Used to
  // locate the offset at which an error begins so its first line can be scrolled into
  // view. This tracks 1:1 with document offsets (filters and folding do not change the
  // underlying text length).
  private final AtomicLong printedCharCount = new AtomicLong(0);

  // Document offset of the first error line to scroll into view, or -1 when there is no
  // pending error. Written on the output thread in print(), consumed on the EDT in
  // scrollToEnd().
  private volatile int pendingErrorStartOffset = -1;

  // True once we have pinned the viewport to an error and frozen auto-follow, so that
  // later errors do not steal focus from the first one. Reset when the user scrolls back
  // to the bottom, allowing a subsequent error to become a fresh target.
  private volatile boolean pinnedToError = false;

  // Guards against the transient "at end" that our own pin-scroll produces on a still-short
  // document: after pinning we wait for the viewport to move away from the end (as the
  // stack trace streams in) before a later return to the end is treated as the user asking
  // to resume following.
  private volatile boolean awaitingScrollAwayFromEnd = false;

  public DaemonConsoleView(@NotNull final Project project, @NotNull final GlobalSearchScope searchScope) {
    super(project, searchScope, true, false);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (!FlutterModuleUtils.hasFlutterModule(getProject())) {
      return;
    }
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      printCounted(text, contentType);
      return;
    }

    // Capture scroll position before queuing new output so scrollToEnd() can
    // honour it once the editor flushes the pending text. Editor geometry must
    // be read on the EDT; if we're already there do it inline, otherwise
    // schedule once (throttled by scrollCheckPending to avoid flooding the EDT
    // during rapid output bursts).
    if (ApplicationManager.getApplication().isDispatchThread()) {
      updateScrollEndState();
    } else if (scrollCheckPending.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        scrollCheckPending.set(false);
        updateScrollEndState();
      }, ModalityState.any());
    }

    if (contentType != ConsoleViewContentType.NORMAL_OUTPUT) {
      // Flush any buffered normal output first so the error is appended after it; the
      // character count now points at the offset where this text will begin.
      writeAvailableLines();
      maybeCaptureErrorStart(text, ConsoleViewContentType.ERROR_OUTPUT.equals(contentType));
      printCounted(text, contentType);
    }
    else {
      stdoutParser.appendOutput(text);
      writeAvailableLines();
    }
  }

  /**
   * Records the current document offset as the scroll target when {@code text} marks the
   * start of an error. We only pin to the first error (until the user returns to the
   * bottom) and only when they were following output at the end; if they have scrolled up
   * to read something, their position is left alone.
   */
  private void maybeCaptureErrorStart(@NotNull String text, boolean isErrorContentType) {
    if (pinnedToError || !wasAtScrollEnd || !isErrorStart(text, isErrorContentType)) {
      return;
    }
    pendingErrorStartOffset = (int)Math.min(printedCharCount.get(), Integer.MAX_VALUE);
    pinnedToError = true;
    awaitingScrollAwayFromEnd = true;
  }

  /**
   * Whether {@code line} marks the beginning of an error. Content routed to the error
   * stream always qualifies; framework exceptions are printed to stdout, so we also match
   * the Flutter error banner text (e.g. "======== Exception caught by rendering library").
   */
  static boolean isErrorStart(@NotNull String line, boolean isErrorContentType) {
    if (isErrorContentType) {
      return true;
    }
    final String lower = line.toLowerCase(Locale.ROOT);
    return lower.contains("exception caught by")
           || lower.contains("another exception was thrown");
  }

  private void printCounted(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    super.print(text, contentType);
    printedCharCount.addAndGet(text.length());
  }

  /**
   * The console is cleared on hot reload/restart. Reset our character count and pin state
   * so document offsets stay valid and a fresh error can be tracked afterwards.
   */
  @Override
  public void clear() {
    super.clear();
    printedCharCount.set(0);
    pendingErrorStartOffset = -1;
    pinnedToError = false;
    awaitingScrollAwayFromEnd = false;
  }

  /**
   * Called by the platform after flushing queued text to the editor.
   *
   * <p>When a new error burst has just arrived, scroll its first line to the top of the
   * viewport and freeze auto-follow so the root-cause error stays visible while the rest
   * of the stack traces stream in below. Otherwise, only scroll to the end when the user
   * was already there; if they have scrolled up, preserve their position.
   */
  @Override
  public void scrollToEnd() {
    final int errorStart = pendingErrorStartOffset;
    if (errorStart >= 0) {
      pendingErrorStartOffset = -1;
      if (scrollErrorStartIntoView(errorStart)) {
        // Freeze auto-follow; the user can resume it by scrolling back to the bottom.
        wasAtScrollEnd = false;
        return;
      }
    }
    if (wasAtScrollEnd) {
      super.scrollToEnd();
    }
  }

  /**
   * Scrolls the editor so that the line containing {@code offset} sits at the top of the
   * visible area. Returns false when there is no editor yet to scroll.
   */
  private boolean scrollErrorStartIntoView(int offset) {
    final Editor rawEditor = getEditor();
    final EditorEx editor = rawEditor instanceof EditorEx ? (EditorEx)rawEditor : null;
    if (editor == null) {
      return false;
    }
    final int clampedOffset = Math.min(offset, editor.getDocument().getTextLength());
    final int targetY = editor.logicalPositionToXY(editor.offsetToLogicalPosition(clampedOffset)).y;
    editor.getScrollingModel().scrollVertically(targetY);
    return true;
  }

  private void updateScrollEndState() {
    final Editor rawEditor = getEditor();
    final EditorEx editor = rawEditor instanceof EditorEx ? (EditorEx) rawEditor : null;
    if (editor == null) {
      return;
    }
    final boolean atEnd = isAtScrollEnd(editor);
    if (!pinnedToError) {
      wasAtScrollEnd = atEnd;
      return;
    }
    // While pinned we stay frozen (wasAtScrollEnd == false) until the user genuinely
    // returns to the bottom. The first move away from the end clears the guard so that a
    // later return counts as a real request to resume following.
    if (!atEnd) {
      awaitingScrollAwayFromEnd = false;
    }
    else if (shouldReleasePin(atEnd, awaitingScrollAwayFromEnd)) {
      pinnedToError = false;
      wasAtScrollEnd = true;
    }
  }

  /**
   * Whether a pinned error should be released, resuming auto-follow. Only true once the
   * viewport has moved away from the end at least once (so the transient "at end" produced
   * by our own pin-scroll does not immediately release the pin).
   */
  static boolean shouldReleasePin(boolean atEnd, boolean awaitingScrollAwayFromEnd) {
    return atEnd && !awaitingScrollAwayFromEnd;
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

        // Framework exceptions arrive here as normal stdout; detect their banner so the
        // first error can be scrolled into view.
        maybeCaptureErrorStart(line, false);
        printCounted(line, ConsoleViewContentType.NORMAL_OUTPUT);
      }
    }
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class AsyncUtils {
  /**
   * Helper to get the value of a future on the UI thread.
   * <p>
   * The action will never be called if the future is cancelled.
   */
  public static <T> void whenCompleteUiThread(CompletableFuture<T> future, BiConsumer<? super T, ? super Throwable> action) {
    future.whenCompleteAsync(
      (T value, Throwable throwable) -> {
        // Exceptions due to the Future being cancelled need to be treated
        // differently as they indicate that no work should be done rather
        // than that an error occurred.
        // By convention we cancel Futures when the value to be computed
        // would be obsolete. For example, the Future may be for a value from
        // a previous Dart isolate or the user has already navigated somewhere
        // else.
        if (throwable instanceof CancellationException) {
          return;
        }
        invokeLater(() -> action.accept(value, throwable));
      }
    );
  }

  public static void invokeLater(Runnable runnable) {
    final Application app = ApplicationManager.getApplication();
    if (app == null || app.isUnitTestMode()) {
      // This case existing to support unit testing.
      SwingUtilities.invokeLater(runnable);
    }
    else {
      app.invokeLater(runnable);
    }
  }

  public static void invokeAndWait(Runnable runnable) throws ProcessCanceledException {
    final Application app = ApplicationManager.getApplication();
    if (app == null || app.isUnitTestMode()) {
      try {
        // This case existing to support unit testing.
        SwingUtilities.invokeAndWait(runnable);
      }
      catch (InterruptedException | InvocationTargetException e) {
        throw new ProcessCanceledException(e);
      }
    }
    else {
      app.invokeAndWait(runnable);
    }
  }
}

/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenApiUtils {

  public static void safeRunReadAction(@NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return;
    application.runReadAction(runnable);
  }

  public static <T> @Nullable T safeRunReadAction(@NotNull Computable<T> computable) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return null;
    return application.runReadAction(computable);
  }
}

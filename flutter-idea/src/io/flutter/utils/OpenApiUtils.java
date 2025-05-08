/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenApiUtils {

  private OpenApiUtils() {
    throw new AssertionError("No instances.");
  }

  public static @NotNull VirtualFile @NotNull [] getContentRoots(@NotNull Module module) {
    var moduleRootManager = ModuleRootManager.getInstance(module);
    return moduleRootManager == null ? VirtualFile.EMPTY_ARRAY : moduleRootManager.getContentRoots();
  }

  public static @Nullable LibraryTable getLibraryTable(@NotNull Project project) {
    var registrar = LibraryTablesRegistrar.getInstance();
    return registrar == null ? null : registrar.getLibraryTable(project);
  }

  public static @NotNull Module @NotNull [] getModules(@NotNull Project project) {
    var modules = ModuleManager.getInstance(project).getModules();
    return modules == null ? Module.EMPTY_ARRAY : modules;
  }

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


  public static void safeInvokeLater(@NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return;
    application.invokeLater(runnable);
  }

  public static void safeInvokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return;
    application.invokeLater(runnable, state);
  }

  public static void safeInvokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition<?> disposed) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return;
    application.invokeLater(runnable, state, disposed);
  }


  public static void safeInvokeAndWait(@NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return;
    application.invokeAndWait(runnable);
  }

  public static <T, E extends Throwable> @Nullable T safeRunWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    Application application = ApplicationManager.getApplication();
    if (application == null) return null;
    return application.runWriteAction(computation);
  }

  public static void safeRunWriteAction(@NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return;
    application.runWriteAction(runnable);
  }
}

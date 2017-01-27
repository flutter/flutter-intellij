/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.DartFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FlutterUtils {
  private FlutterUtils() {
  }

  /**
   * This method exists for compatibility with older IntelliJ API versions.
   * <p>
   * `Application.invokeAndWait(Runnable)` doesn't exist pre 2016.3.
   */
  public static void invokeAndWait(@NotNull Runnable runnable) throws ProcessCanceledException {
    ApplicationManager.getApplication().invokeAndWait(
      runnable,
      ModalityState.defaultModalityState());
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isFlutteryFile(@NotNull VirtualFile file) {
    return isDartFile(file) || isPubspecFile(file);
  }

  public static boolean isPubspecFile(@NotNull VirtualFile file) {
    return Objects.equals(file.getName(), FlutterConstants.PUBSPEC_YAML);
  }

  public static boolean isDartFile(@NotNull VirtualFile file) {
    return Objects.equals(file.getFileType(), DartFileType.INSTANCE);
  }

  public static boolean exists(@Nullable VirtualFile file) {
    return file != null && file.exists();
  }

  @Nullable
  public static VirtualFile getRealVirtualFile(@Nullable PsiFile psiFile) {
    return psiFile != null ? psiFile.getOriginalFile().getVirtualFile() : null;
  }

  public static void openFlutterSettings(@Nullable Project project) {
    ShowSettingsUtilImpl.showSettingsDialog(project, FlutterConstants.FLUTTER_SETTINGS_PAGE_ID, "");
  }
}

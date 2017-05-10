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
import io.flutter.pub.PubRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

public class FlutterUtils {
  private static final Pattern VALID_ID = Pattern.compile("[_a-zA-Z$][_a-zA-Z0-9$]*");
  private static final Pattern VALID_PACKAGE = Pattern.compile("^([a-z]+([_]?[a-z0-9]+)*)+$");

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
    return isDartFile(file) || PubRoot.isPubspec(file);
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

  /**
   * Checks whether a given string is a Dart keyword.
   *
   * @param string the string to check
   * @return true if a keyword, false oetherwise
   */
  public static boolean isDartKeword(@NotNull String string) {
    return FlutterConstants.DART_KEYWORDS.contains(string);
  }

  /**
   * Checks whether a given string is a valid Dart identifier.
   * <p>
   * See: https://www.dartlang.org/guides/language/spec
   *
   * @param id the string to check
   * @return true if a valid identifer, false otherwise.
   */
  public static boolean isValidDartIdentifier(@NotNull String id) {
    return VALID_ID.matcher(id).matches();
  }

  /**
   * Checks whether a given string is a valid Dart package name.
   * <p>
   * See: https://www.dartlang.org/tools/pub/pubspec#name
   *
   * @param name the string to check
   * @return true if a valid package name, false otherwise.
   */
  public static boolean isValidPackageName(@NotNull String name) {
    return VALID_PACKAGE.matcher(name).matches();
  }
}

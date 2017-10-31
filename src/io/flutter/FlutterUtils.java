/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformUtils;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterRunConfigurationProducer;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

public class FlutterUtils {
  private static final Pattern VALID_ID = Pattern.compile("[_a-zA-Z$][_a-zA-Z0-9$]*");
  // Note the possessive quantifiers -- greedy quantifiers are too slow on long expressions (#1421).
  private static final Pattern VALID_PACKAGE = Pattern.compile("^([a-z]++([_]?[a-z0-9]+)*)++$");
  private static final String[] PLUGIN_IDS = { "io.flutter", "io.flutter.as" };

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

  public static boolean isAndroidStudio() {
    return StringUtil.equals(PlatformUtils.getPlatformPrefix(), "AndroidStudio");
  }

  public static boolean exists(@Nullable VirtualFile file) {
    return file != null && file.exists();
  }

  /**
   * Test if the given element is contained in a module with a pub root that declares a flutter dependency.
   */
  public static boolean isInFlutterProject(@NotNull PsiElement element) {
    final Module module = ModuleUtil.findModuleForPsiElement(element);
    return module != null && FlutterModuleUtils.usesFlutter(module);
  }

  public static boolean isInTestDir(DartFile file) {
    final PubRoot root = PubRoot.forPsiFile(file);
    if (root == null) return false;

    if (!FlutterModuleUtils.isFlutterModule(root.getModule(file.getProject()))) return false;

    final VirtualFile candidate = FlutterRunConfigurationProducer.getFlutterEntryFile(file, false, false);
    if (candidate == null) return false;

    final String relativePath = root.getRelativePath(candidate);
    return relativePath != null && relativePath.startsWith("test/");
  }

  @Nullable
  public static VirtualFile getRealVirtualFile(@Nullable PsiFile psiFile) {
    return psiFile != null ? psiFile.getOriginalFile().getVirtualFile() : null;
  }

  /**
   * Returns the Dart file for the given PsiElement, or null if not a match.
   */
  @Nullable
  public static DartFile getDartFile(final @Nullable PsiElement elt) {
    if (elt == null) return null;

    final PsiFile psiFile = elt.getContainingFile();
    if (!(psiFile instanceof DartFile)) return null;

    return (DartFile)psiFile;
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
  public static boolean isDartKeyword(@NotNull String string) {
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

  /**
   * Checks whether a given filename is an Xcode metadata file, suitable for opening externally.
   *
   * @param name the name to check
   * @return true if an xcode project filename
   */
  public static boolean isXcodeFileName(@NotNull String name) {
    return isXcodeProjectFileName(name) || isXcodeWorkspaceFileName(name);
  }

  /**
   * Checks whether a given file name is an Xcode project filename.
   *
   * @param name the name to check
   * @return true if an xcode project filename
   */
  public static boolean isXcodeProjectFileName(@NotNull String name) {
    return name.endsWith(".xcodeproj");
  }

  /**
   * Checks whether a given name is an Xcode workspace filename.
   *
   * @param name the name to check
   * @return true if an xcode workspace filename
   */
  public static boolean isXcodeWorkspaceFileName(@NotNull String name) {
    return name.endsWith(".xcworkspace");
  }

  /**
   * Checks whether the given commandline executes cleanly.
   *
   * @param cmd the command
   * @return true if the command runs cleanly
   */
  public static boolean runsCleanly(@NotNull GeneralCommandLine cmd) {
    try {
      return ExecUtil.execAndGetOutput(cmd).getExitCode() == 0;
    }
    catch (ExecutionException e) {
      return false;
    }
  }

  @NotNull
  public static PluginId getPluginId() {
    for (String id : PLUGIN_IDS) {
      final PluginId pid = PluginId.findId(id);
      if (pid != null) {
        return pid;
      }
    }
    throw new IllegalStateException("no plugin id");
  }
}

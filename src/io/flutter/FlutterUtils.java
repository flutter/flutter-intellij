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
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class FlutterUtils {
  private static final Pattern VALID_ID = Pattern.compile("[_a-zA-Z$][_a-zA-Z0-9$]*");
  // Note the possessive quantifiers -- greedy quantifiers are too slow on long expressions (#1421).
  private static final Pattern VALID_PACKAGE = Pattern.compile("^([a-z]++([_]?[a-z0-9]+)*)++$");

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

  public static boolean isFlutteryFile(@NotNull VirtualFile file) {
    return isDartFile(file) || PubRoot.isPubspec(file);
  }

  public static boolean isDartFile(@NotNull VirtualFile file) {
    return Objects.equals(file.getFileType(), DartFileType.INSTANCE);
  }

  public static boolean isAndroidStudio() {
    return StringUtil.equals(PlatformUtils.getPlatformPrefix(), "AndroidStudio");
  }

  public static boolean is2018_3_or_higher() {
    return getBaselineVersion() >= 183;
  }

  /**
   * Write a warning message to the IntelliJ log.
   * <p>
   * This is separate from LOG.warn() to allow us to decorate the behavior.
   */
  public static void warn(Logger logger, @NotNull Throwable t) {
    logger.warn(t);
  }

  /**
   * Write a warning message to the IntelliJ log.
   * <p>
   * This is separate from LOG.warn() to allow us to decorate the behavior.
   */
  public static void warn(Logger logger, String message) {
    logger.warn(message);
  }

  /**
   * Write a warning message to the IntelliJ log.
   * <p>
   * This is separate from LOG.warn() to allow us to decorate the behavior.
   */
  public static void warn(Logger logger, String message, @NotNull Throwable t) {
    logger.warn(message, t);
  }

  private static int getBaselineVersion() {
    final ApplicationInfo appInfo = ApplicationInfo.getInstance();
    if (appInfo != null) {
      return appInfo.getBuild().getBaselineVersion();
    }
    return -1;
  }

  public static void disableGradleProjectMigrationNotification(@NotNull Project project) {
    final String showMigrateToGradlePopup = "show.migrate.to.gradle.popup";
    final PropertiesComponent properties = PropertiesComponent.getInstance(project);

    if (properties.getValue(showMigrateToGradlePopup) == null) {
      properties.setValue(showMigrateToGradlePopup, "false");
    }
  }

  public static boolean exists(@Nullable VirtualFile file) {
    return file != null && file.exists();
  }

  /**
   * Test if the given element is contained in a module with a pub root that declares a flutter dependency.
   */
  public static boolean isInFlutterProject(@NotNull PsiElement element) {
    final Module module = ModuleUtil.findModuleForPsiElement(element);
    return module != null && FlutterModuleUtils.declaresFlutter(module);
  }

  public static boolean isInTestDir(@Nullable DartFile file) {
    if (file == null) return false;
    final PubRoot root = PubRoot.forFile(file.getVirtualFile());
    if (root == null) return false;

    if (!FlutterModuleUtils.isFlutterModule(root.getModule(file.getProject()))) return false;

    final VirtualFile candidate = FlutterRunConfigurationProducer.getFlutterEntryFile(file, false, false);
    if (candidate == null) return false;

    final String relativePath = root.getRelativePath(candidate);
    return relativePath != null && (relativePath.startsWith("test/"));
  }

  public static boolean isIntegrationTestingMode() {
    return System.getProperty("idea.required.plugins.id", "").equals("io.flutter.tests.gui.flutter-gui-tests");
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
   * @return true if a keyword, false otherwise
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
   *
   * @param name the string to check
   * @return true if a valid package name, false otherwise.
   * @see <a href="www.dartlang.org/tools/pub/pubspec#name">https://www.dartlang.org/tools/pub/pubspec#name</a>
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
    // TODO(devoncarew): Update this as we converge on a new build system.
    //final PluginId pluginId = PluginId.findId(isAndroidStudio() ? "io.flutter.as" : "io.flutter");
    final PluginId pluginId = PluginId.findId("io.flutter");
    assert pluginId != null;
    return pluginId;
  }

  /**
   * Returns true if passed pubspec declares a flutter or flutter_web dependency.
   * <p>
   * This method is provided explicitly instead of calling declaresFlutter(VirtualFile) || declaresFlutterWeb(VirtualFile) so only one read
   * and parsing of the pubspec is made.
   */
  public static boolean declaresFlutterAny(@NotNull final VirtualFile pubspec) {
    // It uses Flutter if it contains:
    // dependencies:
    //   flutter:

    try {
      final Map<String, Object> yamlMap = readPubspecFileToMap(pubspec);
      if (yamlMap == null) {
        return false;
      }

      // Special case the 'flutter' package itself - this allows us to run their unit tests from IntelliJ.
      final Object name = yamlMap.get("name");
      if ("flutter".equals(name)) {
        return true;
      }

      // Check for a dependency on the flutter package.
      final Object dependencies = yamlMap.get("dependencies");
      //noinspection SimplifiableIfStatement
      if (dependencies instanceof Map) {
        return ((Map)dependencies).containsKey("flutter") || ((Map)dependencies).containsKey("flutter_web");
      }

      return false;
    }
    catch (IOException e) {
      return false;
    }
  }

  /**
   * Returns true if passed pubspec declares a flutter dependency.
   */
  public static boolean declaresFlutter(@NotNull final VirtualFile pubspec) {
    // It uses Flutter if it contains:
    // dependencies:
    //   flutter:

    try {
      final Map<String, Object> yamlMap = readPubspecFileToMap(pubspec);
      if (yamlMap == null) {
        return false;
      }

      // Special case the 'flutter' package itself - this allows us to run their unit tests from IntelliJ.
      final Object name = yamlMap.get("name");
      if ("flutter".equals(name)) {
        return true;
      }

      // Check for a dependency on the flutter package.
      final Object dependencies = yamlMap.get("dependencies");
      //noinspection SimplifiableIfStatement
      if (dependencies instanceof Map) {
        return ((Map)dependencies).containsKey("flutter");
      }

      return false;
    }
    catch (IOException e) {
      return false;
    }
  }

  /**
   * Returns true if passed pubspec declares a flutter_web dependency.
   */
  public static boolean declaresFlutterWeb(@NotNull final VirtualFile pubspec) {
    // It uses Flutter if it contains:
    // dependencies:
    //   flutter_web:

    try {
      final Map<String, Object> yamlMap = readPubspecFileToMap(pubspec);
      if (yamlMap == null) {
        return false;
      }

      // Check for a dependency on the flutter package.
      final Object dependencies = yamlMap.get("dependencies");
      //noinspection SimplifiableIfStatement
      if (dependencies instanceof Map) {
        return ((Map)dependencies).containsKey("flutter_web");
      }

      return false;
    }
    catch (IOException e) {
      return false;
    }
  }

  /**
   * Returns true if the passed pubspec indicates that it is a Flutter plugin.
   */
  public static boolean isFlutterPlugin(@NotNull final VirtualFile pubspec) {
    // It's a plugin if it contains:
    // flutter:
    //   plugin:

    try {
      final Map<String, Object> yamlMap = readPubspecFileToMap(pubspec);
      if (yamlMap == null) {
        return false;
      }

      final Object flutterEntry = yamlMap.get("flutter");
      //noinspection SimplifiableIfStatement
      if (flutterEntry instanceof Map) {
        return ((Map)flutterEntry).containsKey("plugin");
      }

      return false;
    }
    catch (IOException e) {
      return false;
    }
  }

  /**
   * Return the project located at the <code>path</code> or containing it.
   *
   * @param path The path to a project or one of its files
   * @return The Project located at the path
   */
  @Nullable
  public static Project findProject(@NotNull String path) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (ProjectUtil.isSameProject(path, project)) {
        return project;
      }
    }
    return null;
  }

  private static Map<String, Object> readPubspecFileToMap(@NotNull final VirtualFile pubspec) throws IOException {
    final String contents = new String(pubspec.contentsToByteArray(true /* cache contents */));
    return loadPubspecInfo(contents);
  }

  private static Map<String, Object> loadPubspecInfo(@NotNull String yamlContents) {
    final Yaml yaml = new Yaml(new SafeConstructor(), new Representer(), new DumperOptions(), new Resolver() {
      @Override
      protected void addImplicitResolvers() {
        this.addImplicitResolver(Tag.BOOL, BOOL, "yYnNtTfFoO");
        this.addImplicitResolver(Tag.NULL, NULL, "~nN\u0000");
        this.addImplicitResolver(Tag.NULL, EMPTY, null);
        this.addImplicitResolver(new Tag("tag:yaml.org,2002:value"), VALUE, "=");
        this.addImplicitResolver(Tag.MERGE, MERGE, "<");
      }
    });

    try {
      //noinspection unchecked
      return (Map)yaml.load(yamlContents);
    }
    catch (Exception e) {
      return null;
    }
  }
}

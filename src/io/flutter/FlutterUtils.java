/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.google.common.base.Charsets;
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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.PlatformUtils;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRootCache;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

public class FlutterUtils {
  public static class FlutterPubspecInfo {
    private final long modificationStamp;

    private boolean flutter = false;
    private boolean plugin = false;

    FlutterPubspecInfo(long modificationStamp) {
      this.modificationStamp = modificationStamp;
    }

    public boolean declaresFlutter() {
      return flutter;
    }

    public boolean isFlutterPlugin() {
      return plugin;
    }

    public long getModificationStamp() {
      return modificationStamp;
    }
  }

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

  public static boolean couldContainWidgets(@Nullable VirtualFile file) {
    // Skip temp file used to show things like files downloaded from the VM.
    if (file instanceof LightVirtualFile) {
      return false;
    }
    // TODO(jacobr): we might also want to filter for files not under the
    // current project root.
    return file != null && isDartFile(file);
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
  public static boolean isInFlutterProject(@NotNull Project project, @NotNull PsiElement element) {
    final PubRoot pubRoot = PubRootCache.getInstance(project).getRoot(element.getContainingFile());
    return pubRoot.declaresFlutter();
  }

  public static boolean isInTestDir(@Nullable DartFile file) {
    if (file == null) return false;

    // Check that we're in a pub root.
    final PubRoot root = PubRootCache.getInstance(file.getProject()).getRoot(file.getVirtualFile().getParent());
    if (root == null) return false;

    // Check that we're in a project path that starts with 'test/'.
    final String relativePath = root.getRelativePath(file.getVirtualFile());
    if (relativePath == null || !relativePath.startsWith("test/")) {
      return false;
    }

    // Check that we're in a Flutter module.
    return FlutterModuleUtils.isFlutterModule(root.getModule(file.getProject()));
  }

  public static boolean isIntegrationTestingMode() {
    return System.getProperty("idea.required.plugins.id", "").equals("io.flutter.tests.gui.flutter-gui-tests");
  }

  @Nullable
  public static VirtualFile getRealVirtualFile(@Nullable PsiFile psiFile) {
    return psiFile != null ? psiFile.getOriginalFile().getVirtualFile() : null;
  }

  @NotNull
  public static VirtualFile getProjectRoot(@NotNull Project project) {
    assert !project.isDefault();
    @SystemIndependent String path = project.getBasePath();
    assert path != null;
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    return Objects.requireNonNull(file);
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
    final PluginId pluginId = PluginId.findId("io.flutter");
    assert pluginId != null;
    return pluginId;
  }

  /**
   * Returns a structured object with information about the Flutter properties of the given
   * pubspec file.
   */
  public static FlutterPubspecInfo getFlutterPubspecInfo(@NotNull final VirtualFile pubspec) {
    // It uses Flutter if it contains 'dependencies: flutter'.
    // It's a plugin if it contains 'flutter: plugin'.

    final FlutterPubspecInfo info = new FlutterPubspecInfo(pubspec.getModificationStamp());

    try {
      final Map<String, Object> yamlMap = readPubspecFileToMap(pubspec);
      if (yamlMap != null) {
        // Special case the 'flutter' package itself - this allows us to run their unit tests from IntelliJ.
        final Object packageName = yamlMap.get("name");
        if ("flutter".equals(packageName)) {
          info.flutter = true;
        }

        // Check the dependencies.
        final Object dependencies = yamlMap.get("dependencies");
        if (dependencies instanceof Map) {
          // We use `|=` for assigning to 'flutter' below as it might have been assigned to true above.
          info.flutter |= ((Map)dependencies).containsKey("flutter");
        }

        // Check for a Flutter plugin.
        final Object flutterEntry = yamlMap.get("flutter");
        if (flutterEntry instanceof Map) {
          info.plugin = ((Map)flutterEntry).containsKey("plugin");
        }
      }
    }
    catch (IOException e) {
      // ignore
    }

    return info;
  }

  /**
   * Returns true if passed pubspec declares a flutter dependency.
   */
  public static boolean declaresFlutter(@NotNull final VirtualFile pubspec) {
    return getFlutterPubspecInfo(pubspec).declaresFlutter();
  }

  /**
   * Returns true if the passed pubspec indicates that it is a Flutter plugin.
   */
  public static boolean isFlutterPlugin(@NotNull final VirtualFile pubspec) {
    return getFlutterPubspecInfo(pubspec).isFlutterPlugin();
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

  public static boolean isAndroidxProject(@NotNull Project project) {
    @SystemIndependent String basePath = project.getBasePath();
    assert basePath != null;
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(basePath);
    assert projectDir != null;
    VirtualFile androidDir = getFlutterManagedAndroidDir(projectDir);
    if (androidDir == null) {
      return false;
    }
    VirtualFile propFile = androidDir.findChild("gradle.properties");
    if (propFile == null) {
      return false;
    }
    Properties properties = new Properties();
    try {
      properties.load(new InputStreamReader(propFile.getInputStream(), Charsets.UTF_8));
    }
    catch (IOException ex) {
      return false;
    }
    String value = properties.getProperty("android.useAndroidX");
    if (value != null) {
      return Boolean.parseBoolean(value);
    }
    return false;
  }

  @Nullable
  private static VirtualFile getFlutterManagedAndroidDir(VirtualFile dir) {
    VirtualFile meta = dir.findChild(".metadata");
    if (meta != null) {
      try {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(meta.getInputStream(), Charsets.UTF_8));
        String value = properties.getProperty("project_type");
        switch (value) {
          case "app":
            return dir.findChild("android");
          case "module":
            return dir.findChild(".android");
          case "package":
            return null;
          case "plugin":
            return dir.findFileByRelativePath("example/android");
        }
      }
      catch (IOException e) {
        // fall thru
      }
    }
    VirtualFile android;
    android = dir.findChild(".android");
    if (android != null) {
      return android;
    }
    android = dir.findChild("android");
    if (android != null) {
      return android;
    }
    android = dir.findFileByRelativePath("example/android");
    if (android != null) {
      return android;
    }
    return null;
  }

  @Nullable
  public static Module findModuleNamed(@NotNull Project project, @NotNull String name) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      if (module.getName().equals(name)) {
        return module;
      }
    }
    return null;
  }

  @Nullable
  public static Module findFlutterGradleModule(@NotNull Project project) {
    Module module = findModuleNamed(project, "flutter");
    if (module == null) {
      return null;
    }
    if (module.getModuleFilePath().endsWith(".android/Flutter/flutter.iml")) {
      VirtualFile file = module.getModuleFile();
      if (file == null) {
        return null;
      }
      file = file.getParent().getParent().getParent();
      VirtualFile meta = file.findChild(".metadata");
      if (meta == null) {
        return null;
      }
      VirtualFile android = getFlutterManagedAndroidDir(meta.getParent());
      if (android != null && android.getName().equals(".android")) {
        return module; // Only true for Flutter modules.
      }
    }
    return null;
  }
}

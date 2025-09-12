/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.google.common.base.Charsets;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.jxbrowser.EmbeddedJxBrowser;
import io.flutter.jxbrowser.JxBrowserStatus;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRootCache;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AndroidUtils;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.EmbeddedBrowser;
import io.flutter.view.EmbeddedJcefBrowser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

public class FlutterUtils {
  public static class FlutterPubspecInfo {
    private final long modificationStamp;

    private boolean flutter = false;
    private boolean plugin = false;
    private boolean resolutionWorkspace = false;

    FlutterPubspecInfo(long modificationStamp) {
      this.modificationStamp = modificationStamp;
    }

    public boolean declaresFlutter() {
      return flutter;
    }

    public boolean isFlutterPlugin() {
      return plugin;
    }

    public boolean isResolutionWorkspace() {
      return resolutionWorkspace;
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

  public static boolean isDartFile(@NotNull VirtualFile file) {
    return Objects.equals(file.getFileType(), DartFileType.INSTANCE);
  }

  public static boolean isAndroidStudio() {
    try {
      // The class is available if the IDE has the "Android" plugin installed or is Android Studio.
      final Class<?> ideInfoClass = Class.forName("com.android.tools.idea.IdeInfo");
      final Method getInstance = ideInfoClass.getMethod("getInstance");
      final Object instance = getInstance.invoke(null);
      final Method isAndroidStudio = ideInfoClass.getMethod("isAndroidStudio");
      return (Boolean)isAndroidStudio.invoke(instance);
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
      // Fall back to checking application name
      // e.g., "Android Studio Meerkat | 2024.3.1"
      return ApplicationInfo.getInstance().getFullApplicationName().startsWith("Android Studio");
    }
  }

  public static void info(@NotNull Logger logger, @NotNull Exception e) {
    info(logger, e, false);
  }

  public static void info(@NotNull Logger logger, @NotNull Exception e, boolean sanitizePaths) {
    if (sanitizePaths && FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
      logger.info(e);
    } else {
      logger.info(e.toString());
    }
  }

  public static void warn(@NotNull Logger logger, @NotNull String message, @NotNull Exception e, boolean sanitizePaths) {
    if (sanitizePaths && FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
      logger.warn(message, e);
    } else {
      logger.warn(message);
    }
  }

  /**
   * Write a warning message to the IntelliJ log.
   * <p>
   * This is separate from LOG.warn() to allow us to decorate the behavior.
   *
   * This method is deprecated (as we are not decorating this behavior anywhere).
   */
  public static void warn(@NotNull Logger logger, @NotNull String message) {
    logger.warn(message);
  }

  /**
   * Write a warning message to the IntelliJ log.
   * <p>
   * This is separate from LOG.warn() to allow us to decorate the behavior.
   *
   * This method is deprecated (as we are not decorating this behavior anywhere).
   */
  public static void warn(@NotNull Logger logger, @NotNull String message, @NotNull Throwable t) {
    logger.warn(message, t);
  }

  public static void disableGradleProjectMigrationNotification(@NotNull Project project) {
    final String showMigrateToGradlePopup = "show.migrate.to.gradle.popup";
    final PropertiesComponent properties = PropertiesComponent.getInstance(project);

    if (properties.getValue(showMigrateToGradlePopup) == null) {
      properties.setValue(showMigrateToGradlePopup, "false");
    }
  }

  /**
   * Test if the given element is contained in a module with a pub root that declares a flutter dependency.
   */
  public static boolean isInFlutterProject(@NotNull Project project, @NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    final PubRoot pubRoot;
    if (file == null) {
      if (element instanceof PsiDirectory) {
        pubRoot = PubRootCache.getInstance(project).getRoot(((PsiDirectory)element).getVirtualFile());
      }
      else {
        return false;
      }
    }
    else {
      pubRoot = PubRootCache.getInstance(project).getRoot(file);
    }
    if (pubRoot == null) {
      return false;
    }
    return pubRoot.declaresFlutter();
  }

  public static boolean isInTestDir(@Nullable DartFile file) {
    if (file == null) return false;

    // Check that we're in a pub root.
    final PubRoot root = PubRootCache.getInstance(file.getProject()).getRoot(file.getVirtualFile().getParent());
    if (root == null) return false;

    //noinspection ConstantConditions
    VirtualFile dir = file.getVirtualFile().getParent();
    if (dir == null) {
      return false;
    }
    final Module module = root.getModule(file.getProject());
    if (!root.hasTests(dir)) {
      if (!isInTestOrSourceRoot(module, file)) {
        return false;
      }
    }

    // Check that we're in a Flutter module.
    return FlutterModuleUtils.isFlutterModule(module);
  }

  private static boolean isInTestOrSourceRoot(@Nullable Module module, @NotNull DartFile file) {
    if (module == null) {
      return false;
    }
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    if (manager == null) {
      return false;
    }
    final ContentEntry[] entries = manager.getContentEntries();
    final VirtualFile virtualFile = file.getContainingFile().getVirtualFile();
    boolean foundSourceRoot = false;
    for (ContentEntry entry : entries) {
      for (SourceFolder folder : entry.getSourceFolders()) {
        final VirtualFile folderFile = folder.getFile();
        if (folderFile == null) {
          continue;
        }
        if (folderFile.equals(VfsUtil.getCommonAncestor(folderFile, virtualFile))) {
          if (folder.getRootType().isForTests()) {
            return true; // Test file is in a directory marked as a test source root, but not named 'test'.
          }
          else {
            foundSourceRoot = true;
            break;
          }
        }
      }
    }
    if (foundSourceRoot) {
      // The file is in a sources root but not marked as tests.
      return file.getName().endsWith(("_test.dart")) && FlutterSettings.getInstance().isAllowTestsInSourcesRoot();
    }
    return false;
  }

  public static boolean isIntegrationTestingMode() {
    return Objects.equals(System.getProperty("idea.required.plugins.id", ""), "io.flutter.tests.gui.flutter-gui-tests");
  }

  @Nullable
  public static VirtualFile getRealVirtualFile(@Nullable PsiFile psiFile) {
    return psiFile != null ? psiFile.getOriginalFile().getVirtualFile() : null;
  }

  @NotNull
  public static VirtualFile getProjectRoot(@NotNull Project project) {
    assert !project.isDefault();
    @SystemIndependent final String path = project.getBasePath();
    assert path != null;
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    return Objects.requireNonNull(file);
  }

  /**
   * Returns the Dart file for the given PsiElement, or null if not a match.
   */
  @Nullable
  public static DartFile getDartFile(@Nullable final PsiElement elt) {
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
   * See: https://dart.dev/guides/language/spec
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
   * @see <a href="dart.dev/tools/pub/pubspec#name">https://dart.dev/tools/pub/pubspec#name</a>
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

  @NotNull
  public static PluginId getPluginId() {
    final PluginId pluginId = PluginId.findId("io.flutter", "");
    assert pluginId != null;
    return pluginId;
  }

  /**
   * Returns a structured object with information about the Flutter properties of the given
   * pubspec file.
   */
  @NotNull
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
          info.flutter |= ((Map<?, ?>)dependencies).containsKey("flutter");
        }

        // Check for a Flutter plugin.
        final Object flutterEntry = yamlMap.get("flutter");
        if (flutterEntry instanceof Map) {
          info.plugin = ((Map<?, ?>)flutterEntry).containsKey("plugin");
        }

        // Check for resolution configuration.
        //  https://dart.dev/tools/pub/workspaces
        final Object resolutionEntry = yamlMap.get("resolution");
        if (resolutionEntry instanceof String) {
          info.resolutionWorkspace = StringUtil.equals((String)resolutionEntry, "workspace");
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
    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager != null) {
      for (Project project : projectManager.getOpenProjects()) {
        if (ProjectUtil.isSameProject(Paths.get(path), project)) {
          return project;
        }
      }
    }
    return null;
  }

  @Nullable
  private static Map<String, Object> readPubspecFileToMap(@NotNull final VirtualFile pubspec) throws IOException {
    final String contents = new String(pubspec.contentsToByteArray(true /* cache contents */));
    return loadPubspecInfo(contents);
  }

  @Nullable
  private static Map<String, Object> loadPubspecInfo(@NotNull String yamlContents) {
    final Yaml yaml =
      new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(new DumperOptions()), new DumperOptions(), new Resolver() {
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
      return yaml.load(yamlContents);
    }
    catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static VirtualFile getFlutterManagedAndroidDir(VirtualFile dir) {
    final VirtualFile meta = dir.findChild(".metadata");
    if (meta != null) {
      try {
        final Properties properties = new Properties();
        properties.load(new InputStreamReader(meta.getInputStream(), Charsets.UTF_8));
        final String value = properties.getProperty("project_type");
        if (value == null) {
          return null;
        }
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
    //noinspection RedundantIfStatement
    if (android != null) {
      return android;
    }
    return null;
  }

  @Nullable
  public static Module findModuleNamed(@NotNull Project project, @NotNull String name) {
    final Module[] modules = OpenApiUtils.getModules(project);
    for (Module module : modules) {
      assert module != null;
      if (module.getName().equals(name)) {
        return module;
      }
    }
    return null;
  }

  @NotNull
  public static String flutterGradleModuleName(@NotNull Project project) {
    return project.getName().replaceAll(" ", "_") + "." + AndroidUtils.FLUTTER_MODULE_NAME;
  }

  @Nullable
  public static Module findFlutterGradleModule(@NotNull Project project) {
    String moduleName = AndroidUtils.FLUTTER_MODULE_NAME;
    Module module = findModuleNamed(project, moduleName);
    if (module == null) {
      moduleName = flutterGradleModuleName(project);
      module = findModuleNamed(project, moduleName);
      if (module == null) {
        return null;
      }
    }
    VirtualFile file = locateModuleRoot(module);
    if (file == null) {
      return null;
    }
    file = file.getParent().getParent();
    final VirtualFile meta = file.findChild(".metadata");
    if (meta == null) {
      return null;
    }
    final VirtualFile android = getFlutterManagedAndroidDir(meta.getParent());
    if (android != null && android.getName().equals(".android")) {
      return module; // Only true for Flutter modules.
    }
    return null;
  }

  @Nullable
  public static VirtualFile locateModuleRoot(@NotNull Module module) {
    final ModuleSourceOrderEntry entry = findModuleSourceEntry(module);
    if (entry == null) return null;
    final VirtualFile[] roots = entry.getRootModel().getContentRoots();
    if (roots.length == 0) return null;
    return roots[0];
  }

  @Nullable
  private static ModuleSourceOrderEntry findModuleSourceEntry(@NotNull Module module) {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (OrderEntry entry : orderEntries) {
      if (entry instanceof ModuleSourceOrderEntry) {
        return (ModuleSourceOrderEntry)entry;
      }
    }
    return null;
  }

  // TODO(helin24): Make other usages of embedded browser initialization safe for UI?
  @Nullable
  public static EmbeddedBrowser embeddedBrowser(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }

    FlutterSettings settings = FlutterSettings.getInstance();

    return settings.isEnableJcefBrowser()
           ? EmbeddedJcefBrowser.getInstance(project)
           : EmbeddedJxBrowser.getInstance(project);
  }

  public static boolean embeddedBrowserAvailable(JxBrowserStatus status) {
    return Objects.equals(status, JxBrowserStatus.INSTALLED) ||
           status.equals(JxBrowserStatus.INSTALLATION_SKIPPED) && FlutterSettings.getInstance()
             .isEnableJcefBrowser();
  }
}

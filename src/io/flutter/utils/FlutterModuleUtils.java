/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import static io.flutter.sdk.FlutterSdk.DART_SDK_SUFFIX;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.PlatformUtils;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.run.FlutterRunConfigurationType;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterModuleUtils {
  public static final String DEPRECATED_FLUTTER_MODULE_TYPE_ID = "WEB_MODULE";

  private FlutterModuleUtils() {
  }

  /**
   * This provides the {@link ModuleType} ID for Flutter modules to be assigned by the {@link io.flutter.module.FlutterModuleBuilder} and
   * elsewhere in the Flutter plugin.
   * <p/>
   * For Flutter module detection however, {@link ModuleType}s should not be used to determine Flutterness.
   */
  @SuppressWarnings("SameReturnValue")
  @NotNull
  public static String getModuleTypeIDForFlutter() {
    return "JAVA_MODULE";
  }

  public static ModuleType getFlutterModuleType() {
    return ModuleTypeManager.getInstance().findByID(getModuleTypeIDForFlutter());
  }

  /**
   * Return true if the passed module is of a Flutter type. Before version M16 this plugin had its own Flutter {@link ModuleType}.
   * Post M16 a Flutter module is defined by the following:
   * <p>
   * <code>
   * [Flutter support enabled for a module] ===
   * [Dart support enabled && referenced Dart SDK is the one inside a Flutter SDK]
   * </code>
   */
  public static boolean isFlutterModule(@Nullable final Module module) {
    if (module == null || module.isDisposed()) return false;

    if (PlatformUtils.isIntelliJ() || FlutterUtils.isAndroidStudio()) {
      // [Flutter support enabled for a module] ===
      //   [Dart support enabled && referenced Dart SDK is the one inside a Flutter SDK]
      final DartSdk dartSdk = DartPlugin.getDartSdk(module.getProject());
      final String dartSdkPath = dartSdk != null ? dartSdk.getHomePath() : null;
      return dartSdkPath != null && dartSdkPath.endsWith(DART_SDK_SUFFIX) && DartPlugin.isDartSdkEnabled(module);
    }
    else {
      // If not IntelliJ, assume a small IDE (no multi-module project support).
      return declaresFlutter(module);
    }
  }

  public static boolean hasFlutterModule(@NotNull Project project) {
    if (project.isDisposed()) return false;

    return CollectionUtils.anyMatch(getModules(project), FlutterModuleUtils::isFlutterModule);
  }

  public static boolean isInFlutterModule(@NotNull PsiElement element) {
    return isFlutterModule(ModuleUtilCore.findModuleForPsiElement(element));
  }

  /**
   * Return the Flutter {@link Workspace} if there is at least one module that is determined to be a Flutter module by the workspace, and
   * has the Dart SDK enabled module.
   */
  @Nullable
  public static Workspace getFlutterBazelWorkspace(@Nullable Project project) {
    if (project == null || project.isDisposed()) return null;

    final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
    if (workspace == null) return null;

    for (Module module : getModules(project)) {
      if (DartPlugin.isDartSdkEnabled(module)) {
        return workspace;
      }
    }

    return null;
  }

  /**
   * Return true if the passed {@link Project} is a Bazel Flutter {@link Project}. If the {@link Workspace} is needed after this call,
   * {@link #getFlutterBazelWorkspace(Project)} should be used.
   */
  public static boolean isFlutterBazelProject(@Nullable Project project) {
    return getFlutterBazelWorkspace(project) != null;
  }

  @Nullable
  public static VirtualFile findXcodeProjectFile(@NotNull Project project) {
    if (project.isDisposed()) return null;

    // Look for XCode metadata file in `ios/`.
    for (PubRoot root : PubRoots.forProject(project)) {
      final VirtualFile dir = root.getiOsDir();
      final VirtualFile file = findPreferedXcodeMetadataFile(dir);
      if (file != null) {
        return file;
      }
    }

    // Look for XCode metadata in `example/ios/`.
    for (PubRoot root : PubRoots.forProject(project)) {
      final VirtualFile exampleDir = root.getExampleDir();
      if (exampleDir != null) {
        VirtualFile iosDir = exampleDir.findChild("ios");
        if (iosDir == null) {
          iosDir = exampleDir.findChild(".ios");
        }
        final VirtualFile file = findPreferedXcodeMetadataFile(iosDir);
        if (file != null) {
          return file;
        }
      }
    }

    return null;
  }

  @Nullable
  private static VirtualFile findPreferedXcodeMetadataFile(@Nullable VirtualFile iosDir) {
    if (iosDir != null) {
      // Prefer .xcworkspace.
      for (VirtualFile child : iosDir.getChildren()) {
        if (FlutterUtils.isXcodeWorkspaceFileName(child.getName())) {
          return child;
        }
      }
      // But fall-back to a project.
      for (VirtualFile child : iosDir.getChildren()) {
        if (FlutterUtils.isXcodeProjectFileName(child.getName())) {
          return child;
        }
      }
    }
    return null;
  }

  @NotNull
  public static Module[] getModules(@NotNull Project project) {
    // A disposed project has no modules.
    if (project.isDisposed()) return new Module[]{};

    return ModuleManager.getInstance(project).getModules();
  }

  /**
   * Check if any module in this project {@link #declaresFlutter(Module)}.
   */
  public static boolean declaresFlutter(@NotNull Project project) {
    if (project.isDisposed()) return false;
    return CollectionUtils.anyMatch(getModules(project), FlutterModuleUtils::declaresFlutter);
  }

  /**
   * Ensures a Flutter run configuration is selected in the run pull down.
   */
  public static void ensureRunConfigSelected(@NotNull Project project) {
    if (project.isDisposed()) return;
    final FlutterRunConfigurationType configType = FlutterRunConfigurationType.getInstance();

    final RunManager runManager = RunManager.getInstance(project);
    if (!runManager.getConfigurationsList(configType).isEmpty()) {
      if (runManager.getSelectedConfiguration() == null) {
        final List<RunnerAndConfigurationSettings> flutterConfigs = runManager.getConfigurationSettingsList(configType);
        if (!flutterConfigs.isEmpty()) {
          runManager.setSelectedConfiguration(flutterConfigs.get(0));
        }
      }
    }
  }

  /**
   * Creates a Flutter run configuration if none exists.
   */
  public static void autoCreateRunConfig(@NotNull Project project, @NotNull PubRoot root) {
    assert ApplicationManager.getApplication().isReadAccessAllowed();
    if (project.isDisposed()) return;

    VirtualFile main = root.getLibMain();
    if (main == null || !main.exists()) {
      // Check for example main.dart in plugins
      main = root.getExampleLibMain();
      if (main == null || !main.exists()) {
        return;
      }
    }

    final FlutterRunConfigurationType configType = FlutterRunConfigurationType.getInstance();
    final RunManager runManager = RunManager.getInstance(project);
    if (!runManager.getConfigurationsList(configType).isEmpty()) {
      // Don't create a run config if one already exists.
      return;
    }

    final RunnerAndConfigurationSettings settings =
      runManager.createRunConfiguration(project.getName(), configType.getFactory());
    final SdkRunConfig config = (SdkRunConfig)settings.getConfiguration();

    // Set config name.
    config.setName("main.dart");

    // Set fields.
    final SdkFields fields = new SdkFields();
    fields.setFilePath(main.getPath());
    config.setFields(fields);

    runManager.addConfiguration(settings, false);
    runManager.setSelectedConfiguration(settings);
  }

  /**
   * If no files are open, show lib/main.dart for the given PubRoot.
   */
  public static void autoShowMain(@NotNull Project project, @NotNull PubRoot root) {
    if (project.isDisposed()) return;

    final VirtualFile main = root.getFileToOpen();
    if (main == null) return;

    DumbService.getInstance(project).runWhenSmart(() -> {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      if (manager.getAllEditors().length == 0) {
        manager.openFile(main, true);
      }
    });
  }

  /**
   * Introspect into the module's content roots, looking for a pubspec.yaml that references flutter.
   * <p/>
   * True is returned if any of the PubRoots associated with the {@link Module} have a pubspec that declares flutter.
   */
  public static boolean declaresFlutter(@NotNull Module module) {
    for (PubRoot root : PubRoots.forModule(module)) {
      if (root.declaresFlutter()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Introspect into the module's content roots, looking for a pubspec.yaml that references flutter web.
   */
  public static boolean declaresFlutterWeb(@NotNull Module module) {
    for (PubRoot root : PubRoots.forModule(module)) {
      if (root.declaresFlutterWeb()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find flutter modules.
   * <p>
   * Flutter modules are defined as:
   * 1. being tagged with the #FlutterModuleType, or
   * 2. containing a pubspec that #declaresFlutterDependency
   */
  @NotNull
  public static List<Module> findModulesWithFlutterContents(@NotNull Project project) {
    return CollectionUtils.filter(getModules(project), module -> isFlutterModule(module) || declaresFlutter(module));
  }

  public static boolean convertFromDeprecatedModuleType(@NotNull Project project) {
    boolean modulesConverted = false;

    // Only automatically convert from older module types to JAVA_MODULE types if we're running in Android Studio.
    if (FlutterUtils.isAndroidStudio()) {
      for (Module module : getModules(project)) {
        if (isDeprecatedFlutterModuleType(module)) {
          setFlutterModuleType(module);
          modulesConverted = true;
        }
      }
    }

    return modulesConverted;
  }

  public static boolean isDeprecatedFlutterModuleType(@NotNull Module module) {
    if (!DEPRECATED_FLUTTER_MODULE_TYPE_ID.equals(module.getOptionValue("type"))) {
      return false;
    }

    // Validate that the pubspec references flutter.
    return FlutterModuleUtils.declaresFlutter(module);
  }

  public static boolean isInFlutterAndroidModule(@NotNull Project project, @NotNull VirtualFile file) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      // contains() should be fast since it uses the index.
      boolean isModuleFile = module.getModuleContentScope().contains(file);
      if (isModuleFile) {
        for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
          if ("Android".equals(facet.getName())) {
            return declaresFlutter(project);
          }
        }
      }
    }
    return false;
  }

  /**
   * Set the passed module to the module type used by Flutter, defined by {@link #getModuleTypeIDForFlutter()}.
   */
  public static void setFlutterModuleType(@NotNull Module module) {
    module.setOption(Module.ELEMENT_TYPE, getModuleTypeIDForFlutter());
  }

  public static void setFlutterModuleAndReload(@NotNull Module module, @NotNull Project project) {
    if (project.isDisposed()) return;

    setFlutterModuleType(module);
    enableDartSDK(module);
    project.save();

    EditorNotifications.getInstance(project).updateAllNotifications();
    ProjectManager.getInstance().reloadProject(project);
  }

  public static void enableDartSDK(@NotNull Module module) {
    if (DartPlugin.isDartSdkEnabled(module)) {
      return;
    }

    // parse the .packages file
    String sdkPath = FlutterSdkUtil.guessFlutterSdkFromPackagesFile(module);
    if (sdkPath != null) {
      FlutterSdkUtil.updateKnownSdkPaths(sdkPath);
    }

    // try and locate flutter on the path
    if (sdkPath == null) {
      sdkPath = FlutterSdkUtil.locateSdkFromPath();
      if (sdkPath != null) {
        FlutterSdkUtil.updateKnownSdkPaths(sdkPath);
      }
    }

    if (sdkPath == null) {
      final String[] flutterSdkPaths = FlutterSdkUtil.getKnownFlutterSdkPaths();
      if (flutterSdkPaths.length > 0) {
        sdkPath = flutterSdkPaths[0];
      }
    }

    if (sdkPath != null) {
      final FlutterSdk flutterSdk = FlutterSdk.forPath(sdkPath);
      if (flutterSdk == null) {
        return;
      }
      final String dartSdkPath = flutterSdk.getDartSdkPath();
      if (dartSdkPath == null) {
        return; // Not cached. TODO call flutterSdk.sync() here?
      }
      ApplicationManager.getApplication().runWriteAction(() -> {
        DartPlugin.ensureDartSdkConfigured(module.getProject(), dartSdkPath);
        DartPlugin.enableDartSdk(module);
      });
    }
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.util.PubspecYamlUtil;
import io.flutter.FlutterUtils;
import io.flutter.actions.FlutterBuildActionGroup;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRootCache;
import io.flutter.pub.PubRoots;
import io.flutter.run.FlutterRunConfigurationType;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterModuleUtils {
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

  public static ModuleType<?> getFlutterModuleType() {
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

    // [Flutter support enabled for a module] ===
    //   [Dart support enabled && referenced Dart SDK is the one inside a Flutter SDK]
    final DartSdk dartSdk = DartPlugin.getDartSdk(module.getProject());
    final String dartSdkPath = dartSdk != null ? dartSdk.getHomePath() : null;
    return validDartSdkPath(dartSdkPath) && DartPlugin.isDartSdkEnabled(module);
  }

  private static boolean validDartSdkPath(String path) {
    return path != null &&
           (path.endsWith(FlutterSdk.DART_SDK_SUFFIX) ||
            path.endsWith(FlutterSdk.LINUX_DART_SUFFIX) ||
            path.endsWith(FlutterSdk.LOCAL_DART_SUFFIX) ||
            path.endsWith(FlutterSdk.MAC_DART_SUFFIX));
  }

  public static boolean hasInternalDartSdkPath(@NotNull Project project) {
    final DartSdk dartSdk = DartPlugin.getDartSdk(project);
    final String dartSdkPath = dartSdk != null ? dartSdk.getHomePath() : "";
    return dartSdkPath.endsWith(FlutterSdk.LINUX_DART_SUFFIX) || dartSdkPath.endsWith(FlutterSdk.MAC_DART_SUFFIX);
  }

  public static boolean hasFlutterModule(@NotNull Project project) {
    if (project.isDisposed()) return false;

    return CollectionUtils.anyMatch(getModules(project), FlutterModuleUtils::isFlutterModule);
  }

  /**
   * Return the Flutter {@link Workspace} if there is at least one module that is determined to be a Flutter module by the workspace, and
   * has the Dart SDK enabled module.
   */
  @Nullable
  public static Workspace getFlutterBazelWorkspace(@Nullable Project project) {
    if (project == null || project.isDisposed()) return null;

    final Workspace workspace = WorkspaceCache.getInstance(project).get();
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
  public static VirtualFile findXcodeProjectFile(@NotNull Project project, @Nullable VirtualFile selectedFile) {
    if (selectedFile == null) {
      // This should not happen because the action should not be visible if there is no selection, but ...
      return findXcodeProjectFile(project);
    }
    VirtualFile dir = selectedFile;
    while (dir != null) {
      String name = dir.getName();
      if ("ios".equals(name) || ".ios".equals(name) || "macos".equals(name)) {
        // If needed, we could add a check that the parent of dir contains pubspec.yaml, but this is probably adequate.
        VirtualFile file = findPreferedXcodeMetadataFile(dir);
        if (file != null) {
          return file;
        }
      }
      dir = dir.getParent();
    }
    return null;
  }

  @Nullable
  private static VirtualFile findXcodeProjectFile(@NotNull Project project) {
    if (project.isDisposed()) return null;

    // Look for Xcode metadata file in `ios/`.
    for (PubRoot root : PubRoots.forProject(project)) {
      final VirtualFile dir = root.getiOsDir();
      final VirtualFile file = findPreferedXcodeMetadataFile(dir);
      if (file != null) {
        return file;
      }
    }

    // Look for Xcode metadata in `example/ios/`.
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


  public static @NotNull Module @NotNull [] getModules(@NotNull Project project) {
    // A disposed project has no modules.
    if (project.isDisposed()) return Module.EMPTY_ARRAY;

    return OpenApiUtils.getModules(project);
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

    final RunnerAndConfigurationSettings settings = runManager.createConfiguration(project.getName(), configType.getFactory());
    final SdkRunConfig config = (SdkRunConfig)settings.getConfiguration();

    // Set config name.
    config.setName("main.dart");

    // Set fields.
    final SdkFields fields = new SdkFields();
    fields.setFilePath(main.getPath());
    config.setFields(fields);

    runManager.addConfiguration(settings);
    runManager.setSelectedConfiguration(settings);
  }

  /**
   * If no files are open, or just the readme, show lib/main.dart for the given PubRoot.
   */
  public static void autoShowMain(@NotNull Project project, @NotNull PubRoot root) {
    if (project.isDisposed()) return;

    final VirtualFile main = root.getFileToOpen();
    if (main == null) return;

    DumbService.getInstance(project).runWhenSmart(() -> {
      final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
      if (fileEditorManager == null) {
        return;
      }
      FileEditor[] editors = fileEditorManager.getAllEditors();
      if (editors.length > 1) {
        return;
      }
      for (FileEditor editor : editors) {
        if (editor == null) {
          return;
        }
        VirtualFile file = editor.getFile();
        if (file != null && file.equals(main) && FlutterUtils.isDartFile(file)) {
          return;
        }
      }
      fileEditorManager.openFile(main, editors.length == 0);
    });
  }

  /**
   * Introspect into the module's content roots, looking for a pubspec.yaml that references flutter.
   * <p/>
   * True is returned if any of the PubRoots associated with the {@link Module} have a pubspec that declares flutter.
   */
  public static boolean declaresFlutter(@NotNull Module module) {
    try {
      final PubRootCache cache = PubRootCache.getInstance(module.getProject());

      for (PubRoot root : cache.getRoots(module)) {
        if (root.declaresFlutter()) {
          return true;
        }
      }

      return false;
    }
    catch (AlreadyDisposedException ignored) {
      return false;
    }
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

  // Return true if there is a module with the same name as the project plus the Android suffix.
  public static boolean hasAndroidModule(@NotNull Project project) {
    for (PubRoot root : PubRoots.forProject(project)) {
      assert root != null;
      String name = PubspecYamlUtil.getDartProjectName(root.getPubspec());
      String moduleName = name + "_android";
      for (Module module : FlutterModuleUtils.getModules(project)) {
        if (moduleName.equals(module.getName())) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInFlutterAndroidModule(@NotNull Project project, @NotNull VirtualFile file) {
    final Module module = FlutterBuildActionGroup.findFlutterModule(project, file);
    if (module != null) {
      for (Facet<?> facet : FacetManager.getInstance(module).getAllFacets()) {
        if ("Android".equals(facet.getName())) {
          return declaresFlutter(project);
        }
      }
    }
    return false;
  }

  /**
   * Set the passed module to the module type used by Flutter, defined by {@link #getModuleTypeIDForFlutter()}.
   */
  public static void setFlutterModuleType(@NotNull Module module) {
    module.setModuleType(getModuleTypeIDForFlutter());
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
    if (FlutterSdk.getFlutterSdk(module.getProject()) != null) {
      return;
    }
    // parse the .dart_tool/flutter_config.json or .packages file
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
      OpenApiUtils.safeRunWriteAction(() -> {
        DartPlugin.ensureDartSdkConfigured(module.getProject(), dartSdkPath);
        DartPlugin.enableDartSdk(module);
      });
    }
  }
}

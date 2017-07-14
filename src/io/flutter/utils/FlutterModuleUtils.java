/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.PlatformUtils;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.module.FlutterModuleType;
import io.flutter.pub.PubRoot;
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

  public static boolean isFlutterModule(@Nullable Module module) {
    // If not IntelliJ, assume a small IDE (no multi-module project support).
    // Look for a module with a flutter-like file structure.
    if (!PlatformUtils.isIntelliJ()) {
      return module != null && usesFlutter(module);
    }
    else {
      return module != null && ModuleType.is(module, FlutterModuleType.getInstance());
    }
  }

  public static boolean isInFlutterModule(@NotNull PsiElement element) {
    return isFlutterModule(ModuleUtil.findModuleForPsiElement(element));
  }

  public static boolean hasFlutterModule(@NotNull Project project) {
    if (ModuleUtil.hasModulesOfType(project, FlutterModuleType.getInstance())) {
      return true;
    }

    // If not IntelliJ, assume a small IDE (no multi-module project support).
    // Look for a module with a flutter-like file structure.
    if (!PlatformUtils.isIntelliJ()) {
      if (CollectionUtils.anyMatch(getModules(project), FlutterModuleUtils::usesFlutter)) {
        return true;
      }
    }

    return false;
  }


  @Nullable
  public static VirtualFile findXcodeProjectFile(@NotNull Project project) {
    for (PubRoot root : PubRoots.forProject(project)) {
      final VirtualFile dir = root.getiOsDir();
      if (dir != null) {
        for (VirtualFile child : dir.getChildren()) {
          if (FlutterUtils.isXcodeProjectFileName(child.getName())) {
            return child;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public static Module[] getModules(@NotNull Project project) {
    return ModuleManager.getInstance(project).getModules();
  }

  /**
   * Check if any module in this project {@link #usesFlutter(Module)}.
   */
  public static boolean usesFlutter(@NotNull Project project) {
    return CollectionUtils.anyMatch(getModules(project), FlutterModuleUtils::usesFlutter);
  }

  /**
   * Creates a Flutter run configuration if none exist.
   */
  public static void autoCreateRunConfig(@NotNull Project project, @NotNull PubRoot root) {
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    final VirtualFile main = root.getLibMain();
    if (main == null || !main.exists()) return;

    final FlutterRunConfigurationType configType = FlutterRunConfigurationType.getInstance();

    final RunManager runManager = RunManager.getInstance(project);
    if (!runManager.getConfigurationsList(configType).isEmpty()) {
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
    final VirtualFile main = root.getLibMain();
    if (main == null) return;

    DumbService.getInstance(project).runWhenSmart(() -> {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      if (manager.getAllEditors().length == 0) {
        manager.openFile(main, true);
      }
    });
  }

  /**
   * Introspect into the module's content roots, looking for flutter.yaml or a pubspec.yaml that
   * references flutter.
   */
  public static boolean usesFlutter(@NotNull Module module) {
    for (PubRoot root : PubRoots.forModule(module)) {
      if (root.declaresFlutter()) {
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
    return CollectionUtils.filter(getModules(project), m -> isFlutterModule(m) || usesFlutter(m));
  }

  public static void setFlutterModuleType(@NotNull Module module) {
    module.setOption(Module.ELEMENT_TYPE, FlutterModuleType.getInstance().getId());
  }

  public static void setFlutterModuleAndReload(@NotNull Module module, @NotNull Project project) {
    setFlutterModuleType(module);
    enableDartSDK(module);
    project.save();

    EditorNotifications.getInstance(project).updateAllNotifications();
    ProjectManager.getInstance().reloadProject(project);
  }

  private static void enableDartSDK(@NotNull Module module) {
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
      if (flutterSdkPaths != null && flutterSdkPaths.length > 0) {
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
        return; // Not cached. TODO(skybrian) call flutterSdk.sync() here?
      }
      ApplicationManager.getApplication().runWriteAction(() -> {
        DartPlugin.ensureDartSdkConfigured(module.getProject(), dartSdkPath);
        DartPlugin.enableDartSdk(module);
      });
    }
  }
}

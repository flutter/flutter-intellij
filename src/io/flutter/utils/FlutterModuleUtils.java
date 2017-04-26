/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.PlatformUtils;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class FlutterModuleUtils {

  private FlutterModuleUtils() {
  }

  public static boolean isFlutterModule(@Nullable Module module) {
    // If not IntelliJ, assume a small IDE (no multi-module project support).
    // Look for a module with a flutter-like file structure.
    if (!PlatformUtils.isIntelliJ()) {
      return module != null && FlutterModuleUtils.usesFlutter(module);
    }
    else {
      return module != null && ModuleType.is(module, FlutterModuleType.getInstance());
    }
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
   * Create a Flutter run configuration.
   *
   * @param project the project
   * @param main    the target main
   */
  public static void createRunConfig(@NotNull Project project, @Nullable VirtualFile main) {
    final ConfigurationFactory[] factories = FlutterRunConfigurationType.getInstance().getConfigurationFactories();
    final Optional<ConfigurationFactory> factory =
      Arrays.stream(factories).filter((f) -> f instanceof FlutterRunConfigurationType.FlutterConfigurationFactory).findFirst();
    assert (factory.isPresent());
    final ConfigurationFactory configurationFactory = factory.get();

    final RunManager runManager = RunManager.getInstance(project);
    final List<RunConfiguration> configurations = runManager.getConfigurationsList(FlutterRunConfigurationType.getInstance());

    // If the target project has no flutter run configurations, create one.
    if (configurations.isEmpty()) {
      final RunnerAndConfigurationSettings settings =
        runManager.createRunConfiguration(project.getName(), configurationFactory);
      final SdkRunConfig config = (SdkRunConfig)settings.getConfiguration();

      // Set config name.
      final String name = config.suggestedName();
      if (name == null) {
        config.setName(project.getName());
      }

      // Set fields.
      final SdkFields fields = new SdkFields();
      if (main != null && main.exists()) {
        fields.setFilePath(main.getPath());
      }
      config.setFields(fields);

      runManager.addConfiguration(settings, false);
      runManager.setSelectedConfiguration(settings);
    }
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
    final String[] flutterSdkPaths = FlutterSdkUtil.getKnownFlutterSdkPaths();
    if (flutterSdkPaths == null || flutterSdkPaths.length == 0) {
      return;
    }
    final FlutterSdk flutterSdk = FlutterSdk.forPath(flutterSdkPaths[0]);
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

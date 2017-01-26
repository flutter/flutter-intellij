/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.PlatformUtils;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.module.FlutterModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlutterModuleUtils {

  private static final Pattern FLUTTER_SDK_DEP = Pattern.compile(".*sdk:\\s*flutter"); //NON-NLS

  private FlutterModuleUtils() {
  }

  public static boolean isFlutterModule(@Nullable Module module) {
    return module != null && ModuleType.is(module, FlutterModuleType.getInstance());
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
   * Introspect into the module's content roots, looking for flutter.yaml or a pubspec.yaml that
   * references flutter.
   */
  public static boolean usesFlutter(@NotNull Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile baseDir : roots) {
      final VirtualFile pubspec = baseDir.findChild(FlutterConstants.PUBSPEC_YAML);
      if (declaresFlutterDependency(pubspec)) {
        return true;
      }
    }
    return false;
  }

  public static boolean declaresFlutterDependency(@Nullable VirtualFile pubspec) {
    if (!FlutterUtils.exists(pubspec)) {
      return false;
    }

    try {
      final String contents = new String(pubspec.contentsToByteArray(true /* cache contents */));
      if (FLUTTER_SDK_DEP.matcher(contents).find()) {
        return true;
      }
    }
    catch (IOException e) {
      // Ignore IO exceptions.
    }
    return false;
  }

  @Nullable
  public static VirtualFile findPackagesFileFrom(@NotNull Project project,
                                                 @SuppressWarnings("SameParameterValue") @Nullable PsiFile psiFile)
    throws ExecutionException {
    if (psiFile == null) {
      final List<VirtualFile> packagesFiles = findPackagesFiles(getModules(project));
      if (packagesFiles.isEmpty()) {
        return null;
      }
      else if (packagesFiles.size() == 1) {
        return packagesFiles.get(0);
      }
      else {
        throw new ExecutionException(FlutterBundle.message("multiple.packages.files.error"));
      }
    }
    final VirtualFile file = psiFile.getVirtualFile();
    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(file);
    return contentRoot == null ? null : contentRoot.findChild(FlutterConstants.PACKAGES_FILE);
  }

  @Nullable
  public static VirtualFile findPubspecFrom(@NotNull Project project, @Nullable PsiFile psiFile) throws ExecutionException {
    if (psiFile == null) {
      final List<VirtualFile> pubspecs = findPubspecs(getModules(project));
      if (pubspecs.isEmpty()) {
        return null;
      }
      else if (pubspecs.size() == 1) {
        return pubspecs.get(0);
      }
      else {
        throw new ExecutionException(FlutterBundle.message("multiple.pubspecs.error"));
      }
    }
    final VirtualFile file = psiFile.getVirtualFile();
    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(file);
    return contentRoot == null ? null : contentRoot.findChild(FlutterConstants.PUBSPEC_YAML);
  }

  @NotNull
  private static List<VirtualFile> findModuleFiles(@NotNull Module[] modules, Function<Module, VirtualFile> finder) {
    return Arrays.stream(modules).flatMap(m -> {
      final VirtualFile file = finder.apply(m);
      return file != null ? Stream.of(file) : Stream.empty();
    }).collect(Collectors.toList());
  }

  @NotNull
  public static List<VirtualFile> findPubspecs(@NotNull Module[] modules) {
    return findModuleFiles(modules, FlutterModuleUtils::findPubspecFrom);
  }

  @NotNull
  public static List<VirtualFile> findPackagesFiles(@NotNull Module[] modules) {
    return findModuleFiles(modules, FlutterModuleUtils::findPackagesFileFrom);
  }

  @Nullable
  public static VirtualFile findPubspecFrom(@Nullable Module module) {
    return findFilesInContentRoots(module, dir -> dir.findChild(FlutterConstants.PUBSPEC_YAML));
  }

  @Nullable
  public static VirtualFile findPackagesFileFrom(@Nullable Module module) {
    return findFilesInContentRoots(module, dir -> dir.findChild(FlutterConstants.PACKAGES_FILE));
  }

  @Nullable
  public static VirtualFile findFilesInContentRoots(@Nullable Module module, @NotNull Function<VirtualFile, VirtualFile> finder) {
    if (module == null) {
      return null;
    }
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    for (VirtualFile dir : moduleRootManager.getContentRoots()) {
      final VirtualFile file = finder.apply(dir);
      if (file != null) {
        return file;
      }
    }
    return null;
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

    if (DartPlugin.getDartSdk(project) != null && !DartPlugin.isDartSdkEnabled(module)) {
      ApplicationManager.getApplication().runWriteAction(() -> DartPlugin.enableDartSdk(module));
    }

    project.save();

    EditorNotifications.getInstance(project).updateAllNotifications();
    ProjectManager.getInstance().reloadProject(project);
  }
}

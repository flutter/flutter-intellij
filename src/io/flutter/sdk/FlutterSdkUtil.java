/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUpdateOption;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPlugin;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class FlutterSdkUtil {
  /**
   * The environment variable to use to tell the flutter tool which app is driving it.
   */
  public static final String FLUTTER_HOST_ENV = "FLUTTER_HOST_ENV";

  private static final Map<Pair<File, Long>, String> ourVersions = new HashMap<>();
  private static final String FLUTTER_SDK_KNOWN_PATHS = "FLUTTER_SDK_KNOWN_PATHS";
  private static final Logger LOG = Logger.getInstance(FlutterSdkUtil.class);

  private FlutterSdkUtil() {
  }

  /**
   * Return the environment variable value to use when shelling out to the Flutter command-line tool.
   */
  public static String getFlutterHostEnvValue() {
    final String clientId = ApplicationNamesInfo.getInstance().getFullProductName().replaceAll(" ", "-");
    final String existingVar = System.getenv(FLUTTER_HOST_ENV);
    return existingVar == null ? clientId : (existingVar + ":" + clientId);
  }

  public static void updateKnownSdkPaths(@NotNull final String newSdkPath) {
    updateKnownPaths(FLUTTER_SDK_KNOWN_PATHS, newSdkPath);
  }

  private static void updateKnownPaths(@SuppressWarnings("SameParameterValue") @NotNull final String propertyKey,
                                       @NotNull final String newPath) {
    final Set<String> allPaths = new LinkedHashSet<>();

    // Add the new value first; this ensures that it's the 'default' flutter sdk.
    allPaths.add(newPath);

    // Add the existing known paths.
    final String[] oldPaths = PropertiesComponent.getInstance().getValues(propertyKey);
    if (oldPaths != null) {
      allPaths.addAll(Arrays.asList(oldPaths));
    }

    // Store the values back.
    if (allPaths.isEmpty()) {
      PropertiesComponent.getInstance().unsetValue(propertyKey);
    }
    else {
      PropertiesComponent.getInstance().setValues(propertyKey, ArrayUtil.toStringArray(allPaths));
    }
  }

  public static void addKnownSDKPathsToCombo(@NotNull JComboBox combo) {
    final Set<String> validPathsForUI = new HashSet<>();
    final String currentPath = combo.getEditor().getItem().toString().trim();

    if (!currentPath.isEmpty()) {
      validPathsForUI.add(currentPath);
    }

    final String[] knownPaths = getKnownFlutterSdkPaths();
    if (knownPaths != null && knownPaths.length > 0) {
      for (String path : knownPaths) {
        if (FlutterSdk.forPath(path) != null) {
          validPathsForUI.add(FileUtil.toSystemDependentName(path));
        }
      }
    }

    //noinspection unchecked
    combo.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(validPathsForUI)));

    if (combo.getSelectedIndex() == -1 && combo.getItemCount() > 0) {
      combo.setSelectedIndex(0);
    }
  }

  @Nullable
  public static String[] getKnownFlutterSdkPaths() {
    return PropertiesComponent.getInstance().getValues(FLUTTER_SDK_KNOWN_PATHS);
  }

  @NotNull
  public static String pathToFlutterTool(@NotNull String sdkPath) throws ExecutionException {
    final String path = findDescendant(sdkPath, "/bin/" + flutterScriptName());
    if (path == null) {
      throw new ExecutionException("Flutter SDK is not configured");
    }
    return path;
  }

  @NotNull
  public static String flutterScriptName() {
    return SystemInfo.isWindows ? "flutter.bat" : "flutter";
  }

  /**
   * Returns the path to the Dart SDK within a Flutter SDK, or null if it doesn't exist.
   */
  @Nullable
  public static String pathToDartSdk(@NotNull String flutterSdkPath) {
    return findDescendant(flutterSdkPath, "/bin/cache/dart-sdk");
  }

  @Nullable
  private static String findDescendant(@NotNull String flutterSdkPath, @NotNull String path) {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(flutterSdkPath + path);
    if (file == null || !file.exists()) {
      return null;
    }
    return file.getPath();
  }

  public static boolean isFlutterSdkHome(@NotNull final String path) {
    final File flutterPubspecFile = new File(path + "/packages/flutter/pubspec.yaml");
    final File flutterToolFile = new File(path + "/bin/flutter");
    final File dartLibFolder = new File(path + "/bin/cache/dart-sdk/lib");
    return flutterPubspecFile.isFile() && flutterToolFile.isFile() && dartLibFolder.isDirectory();
  }

  private static boolean isFlutterSdkHomeWithoutDartSdk(@NotNull final String path) {
    final File flutterPubspecFile = new File(path + "/packages/flutter/pubspec.yaml");
    final File flutterToolFile = new File(path + "/bin/flutter");
    final File dartLibFolder = new File(path + "/bin/cache/dart-sdk/lib");
    return flutterPubspecFile.isFile() && flutterToolFile.isFile() && !dartLibFolder.isDirectory();
  }

  @NotNull
  public static String versionPath(@NotNull String sdkHomePath) {
    return sdkHomePath + "/VERSION";
  }

  /**
   * Checks the workspace for any open Flutter projects.
   *
   * @return true if an open Flutter project is found
   */
  public static boolean hasFlutterModules() {
    return Arrays.stream(ProjectManager.getInstance().getOpenProjects()).anyMatch(FlutterModuleUtils::hasFlutterModule);
  }

  public static boolean hasFlutterModules(@NotNull Project project) {
    return FlutterModuleUtils.hasFlutterModule(project);
  }

  @Nullable
  public static String getSdkVersion(@NotNull String sdkHomePath) {
    final File versionFile = new File(versionPath(sdkHomePath));
    final Pair<File, Long> key = Pair.create(versionFile, versionFile.lastModified());
    if (ourVersions.containsKey(key)) {
      return ourVersions.get(key);
    }

    final String version = readVersionFile(sdkHomePath);
    if (version == null) {
      LOG.warn("Unable to find Flutter SDK version at " + sdkHomePath);
    }

    ourVersions.put(Pair.create(versionFile, versionFile.lastModified()), version);
    return version;
  }

  private static String readVersionFile(String sdkHomePath) {
    final File versionFile = new File(versionPath(sdkHomePath));
    if (versionFile.isFile() && versionFile.length() < 1000) {
      try {
        final String content = FileUtil.loadFile(versionFile).trim();
        final int index = content.lastIndexOf('\n');
        if (index < 0) return content;
        return content.substring(index + 1).trim();
      }
      catch (IOException e) {
        /* ignore */
      }
    }
    return null;
  }

  @Nullable
  public static String getErrorMessageIfWrongSdkRootPath(final @NotNull String sdkRootPath) {
    if (sdkRootPath.isEmpty()) {
      return null;
    }

    final File sdkRoot = new File(sdkRootPath);
    if (!sdkRoot.isDirectory()) return FlutterBundle.message("error.folder.specified.as.sdk.not.exists");

    if (isFlutterSdkHomeWithoutDartSdk(sdkRootPath)) return FlutterBundle.message("error.flutter.sdk.without.dart.sdk");
    if (!isFlutterSdkHome(sdkRootPath)) return FlutterBundle.message("error.sdk.not.found.in.specified.location");

    return null;
  }

  public static void setFlutterSdkPath(@NotNull final Project project, @NotNull final String flutterSdkPath) {
    // In reality this method sets Dart SDK (that is inside the Flutter SDK).
    final String dartSdk = flutterSdkPath + "/bin/cache/dart-sdk";
    ApplicationManager.getApplication().runWriteAction(() -> DartPlugin.ensureDartSdkConfigured(project, dartSdk));

    // Checking for updates doesn't make sense since the channels don't correspond to Flutter...
    DartSdkUpdateOption.setDartSdkUpdateOption(DartSdkUpdateOption.DoNotCheck);

    // Update the list of known sdk paths.
    FlutterSdkUtil.updateKnownSdkPaths(flutterSdkPath);

    // Fire events for a Flutter SDK change, which updates the UI.
    FlutterSdkManager.getInstance(project).checkForFlutterSdkChange();
  }

  // TODO(devoncarew): The Dart plugin supports specifying individual modules in the settings page.

  /**
   * Do a best-effort basis to enable Dart support for the given project.
   */
  public static void enableDartSdk(@NotNull final Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 1) {
      DartPlugin.enableDartSdk(modules[0]);
    }
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ExecutionException;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.jetbrains.lang.dart.sdk.DartSdkUpdateOption;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.JsonUtils;
import io.flutter.utils.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class FlutterSdkUtil {
  /**
   * The environment variable to use to tell the flutter tool which app is driving it.
   */
  public static final String FLUTTER_HOST_ENV = "FLUTTER_HOST";

  private static final String FLUTTER_SDK_KNOWN_PATHS = "FLUTTER_SDK_KNOWN_PATHS";
  private static final Logger LOG = Logger.getInstance(FlutterSdkUtil.class);
  private static final String FLUTTER_SNAP_SDK_PATH = "/snap/flutter/common/flutter";

  private FlutterSdkUtil() {
  }

  /**
   * Return the environment variable value to use when shelling out to the Flutter command-line tool.
   */
  public static String getFlutterHostEnvValue() {
    final String clientId = ApplicationNamesInfo.getInstance().getFullProductName().replaceAll(" ", "-");
    final String existingVar = java.lang.System.getenv(FLUTTER_HOST_ENV);
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

    final PropertiesComponent props = PropertiesComponent.getInstance();

    // Add the existing known paths.
    final String[] oldPaths = props.getValues(propertyKey);
    if (oldPaths != null) {
      allPaths.addAll(Arrays.asList(oldPaths));
    }

    // Store the values back.
    if (allPaths.isEmpty()) {
      props.unsetValue(propertyKey);
    }
    else {
      props.setValues(propertyKey, ArrayUtil.toStringArray(allPaths));
    }
  }

  /**
   * Adds the current path and other known paths to the combo, most recently used first.
   */
  public static void addKnownSDKPathsToCombo(@NotNull JComboBox combo) {
    final Set<String> pathsToShow = new LinkedHashSet<>();

    final String currentPath = combo.getEditor().getItem().toString().trim();
    if (!currentPath.isEmpty()) {
      pathsToShow.add(currentPath);
    }

    final String[] knownPaths = getKnownFlutterSdkPaths();
    for (String path : knownPaths) {
      if (FlutterSdk.forPath(path) != null) {
        pathsToShow.add(FileUtil.toSystemDependentName(path));
      }
    }

    //noinspection unchecked
    combo.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(pathsToShow)));

    if (combo.getSelectedIndex() == -1 && combo.getItemCount() > 0) {
      combo.setSelectedIndex(0);
    }
  }

  @NotNull
  public static String[] getKnownFlutterSdkPaths() {
    final Set<String> paths = new HashSet<>();

    // scan current projects for existing flutter sdk settings
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
      if (flutterSdk != null) {
        paths.add(flutterSdk.getHomePath());
      }
    }

    // use the list of paths they've entered in the past
    final String[] knownPaths = PropertiesComponent.getInstance().getValues(FLUTTER_SDK_KNOWN_PATHS);
    if (knownPaths != null) {
      paths.addAll(Arrays.asList(knownPaths));
    }

    // search the user's path
    final String fromUserPath = locateSdkFromPath();
    if (fromUserPath != null) {
      paths.add(fromUserPath);
    }

    // Add the snap SDK path if it exists; note that this path is standard on all Linux platforms.
    final File snapSdkPath = new File(System.getenv("HOME") + FLUTTER_SNAP_SDK_PATH);
    if (snapSdkPath.exists()) {
      paths.add(snapSdkPath.getAbsolutePath());
    }

    return paths.toArray(new String[0]);
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

  /**
   * Parse any .packages file and infer the location of the Flutter SDK from that.
   */
  @Nullable
  public static String guessFlutterSdkFromPackagesFile(@NotNull Module module) {
    // First, look for .dart_tool/package_config.json
    for (PubRoot pubRoot : PubRoots.forModule(module)) {
      final VirtualFile packagesFile = pubRoot.getPackageConfigFile();
      if (packagesFile == null) {
        continue;
      }
      // parse it
      try {
        final String contents = new String(packagesFile.contentsToByteArray(true /* cache contents */));
        final JsonElement element = new JsonParser().parse(contents);
        if (element == null) {
          continue;
        }
        final JsonObject json = element.getAsJsonObject();
        if (JsonUtils.getIntMember(json, "configVersion") < 2) continue;
        final JsonArray packages = json.getAsJsonArray("packages");
        if (packages == null || packages.size() == 0) {
          continue;
        }
        for (int i = 0; i < packages.size(); i++) {
          final JsonObject pack = packages.get(i).getAsJsonObject();
          if ("flutter".equals(JsonUtils.getStringMember(pack, "name"))) {
            final String uri = JsonUtils.getStringMember(pack, "rootUri");
            if (uri == null) {
              continue;
            }
            final String path = extractSdkPathFromUri(uri, false);
            if (path == null) {
              continue;
            }
            return path;
          }
        }
      }
      catch (IOException ignored) {
      }
    }

    // Next, try the obsolete .packages
    for (PubRoot pubRoot : PubRoots.forModule(module)) {
      final VirtualFile packagesFile = pubRoot.getPackagesFile();
      if (packagesFile == null) {
        continue;
      }
      // parse it
      try {
        final String contents = new String(packagesFile.contentsToByteArray(true /* cache contents */));
        return parseFlutterSdkPath(contents);
      }
      catch (IOException ignored) {
      }
    }

    return null;
  }

  @VisibleForTesting
  public static String parseFlutterSdkPath(String packagesFileContent) {
    for (String line : packagesFileContent.split("\n")) {
      // flutter:file:///Users/.../flutter/packages/flutter/lib/
      line = line.trim();

      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      final String flutterPrefix = "flutter:";
      if (line.startsWith(flutterPrefix)) {
        final String urlString = line.substring(flutterPrefix.length());
        final String path = extractSdkPathFromUri(urlString, true);
        if (path == null) {
          continue;
        }
        return path;
      }
    }

    return null;
  }

  private static String extractSdkPathFromUri(String urlString, boolean isLibIncluded) {
    if (urlString.startsWith("file:")) {
      final Url url = Urls.parseEncoded(urlString);
      if (url == null) {
        return null;
      }
      final String path = url.getPath();
      // go up three levels for .packages or two for .dart_tool/package_config.json
      File file = new File(url.getPath());
      file = file.getParentFile().getParentFile();
      if (isLibIncluded) {
        file = file.getParentFile();
      }
      return file.getPath();
    }
    return null;
  }

  /**
   * Locate the Flutter SDK using the user's PATH.
   */
  @Nullable
  public static String locateSdkFromPath() {
    final String flutterBinPath = SystemUtils.which("flutter");
    if (flutterBinPath == null) {
      return null;
    }

    final File flutterBinFile = new File(flutterBinPath);
    return flutterBinFile.getParentFile().getParentFile().getPath();
  }
}

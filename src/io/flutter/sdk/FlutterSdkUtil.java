/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.sdk.DartSdkGlobalLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUpdateOption;
import gnu.trove.THashSet;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;


public class FlutterSdkUtil {
  private static final Map<Pair<File, Long>, String> ourVersions = new HashMap<>();
  private static final String FLUTTER_SDK_KNOWN_PATHS = "FLUTTER_SDK_KNOWN_PATHS";
  private static final Logger LOG = Logger.getInstance(FlutterSdkUtil.class);

  private FlutterSdkUtil() {
  }

  public static void updateKnownSdkPaths(@NotNull final String newSdkPath) {
    updateKnownPaths(FLUTTER_SDK_KNOWN_PATHS, newSdkPath);
  }

  private static void updateKnownPaths(@SuppressWarnings("SameParameterValue") @NotNull final String propertyKey,
                                       @NotNull final String newPath) {
    final Set<String> known = new THashSet<>();

    final String[] oldKnownPaths = PropertiesComponent.getInstance().getValues(propertyKey);
    if (oldKnownPaths != null) {
      known.addAll(Arrays.asList(oldKnownPaths));
    }

    known.add(newPath);

    if (known.isEmpty()) {
      PropertiesComponent.getInstance().unsetValue(propertyKey);
    }
    else {
      PropertiesComponent.getInstance().setValues(propertyKey, ArrayUtil.toStringArray(known));
    }
  }

  public static void addKnownSDKPathsToCombo(@NotNull JComboBox combo) {
    final Set<String> validPathsForUI = new HashSet<>();
    final String currentPath = combo.getEditor().getItem().toString().trim();

    if (!currentPath.isEmpty()) {
      validPathsForUI.add(currentPath);
    }

    final String[] knownPaths = PropertiesComponent.getInstance().getValues(FLUTTER_SDK_KNOWN_PATHS);
    if (knownPaths != null && knownPaths.length > 0) {
      for (String path : knownPaths) {
        if (FlutterSdk.forPath(path) != null) {
          validPathsForUI.add(FileUtil.toSystemDependentName(path));
        }
      }
    }

    //noinspection unchecked
    combo.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(validPathsForUI)));
  }

  @NotNull
  public static String pathToFlutterTool(@NotNull String sdkPath) throws ExecutionException {
    // TODO: Use flutter.bat on Windows.
    return sdkRelativePathTo(sdkPath, "bin", "flutter");
  }

  @NotNull
  public static String pathToDartSdk(@NotNull String sdkPath) throws ExecutionException {
    return sdkRelativePathTo(sdkPath, "bin", "cache", "dart-sdk");
  }

  @NotNull
  private static String sdkRelativePathTo(@NotNull String sdkPath, @NotNull String... segments) throws ExecutionException {
    VirtualFile child = LocalFileSystem.getInstance().findFileByPath(sdkPath);
    if (child == null) throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    for (String segment : segments) {
      child = child.findChild(segment);
      if (child == null) {
        throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
      }
    }
    return child.getPath();
  }

  public static boolean isFlutterSdkHome(@NotNull final String path) {
    final File flutterVersionFile = new File(path + "/VERSION");
    final File flutterToolFile = new File(path + "/bin/flutter");
    final File dartLibFolder = new File(path + "/bin/cache/dart-sdk/lib");
    return flutterVersionFile.isFile() && flutterToolFile.isFile() && dartLibFolder.isDirectory();
  }

  private static boolean isFlutterSdkHomeWithoutDartSdk(@NotNull final String path) {
    final File flutterVersionFile = new File(path + "/VERSION");
    final File flutterToolFile = new File(path + "/bin/flutter");
    final File dartLibFolder = new File(path + "/bin/cache/dart-sdk/lib");
    return flutterVersionFile.isFile() && flutterToolFile.isFile() && !dartLibFolder.isDirectory();
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

  @Nullable
  public static String getSdkVersion(@NotNull String sdkHomePath) {
    final File versionFile = new File(versionPath(sdkHomePath));
    if (versionFile.isFile()) {
      final String cachedVersion = ourVersions.get(Pair.create(versionFile, versionFile.lastModified()));
      if (cachedVersion != null) return cachedVersion;
    }

    final String version = readVersionFile(sdkHomePath);
    if (version != null) {
      ourVersions.put(Pair.create(versionFile, versionFile.lastModified()), version);
      return version;
    }

    LOG.warn("Unable to find Flutter SDK version at " + sdkHomePath);
    return null;
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

  public static boolean isFlutterProjectDir(@Nullable VirtualFile dir) {
    if (dir == null || !dir.isDirectory()) return false;

    final VirtualFile pubspec = dir.findChild(FlutterConstants.PUBSPEC_YAML);
    return FlutterModuleUtils.declaresFlutterDependency(pubspec);
  }

  public static void setFlutterSdkPath(@NotNull final String flutterSdkPath) {
    // In reality this method sets Dart SDK, that is inside the Flutter SDK;
    final String dartSdk = flutterSdkPath + "/bin/cache/dart-sdk";
    ApplicationManager.getApplication().runWriteAction(() -> DartSdkGlobalLibUtil.ensureDartSdkConfigured(dartSdk));

    // Checking for updates doesn't make sense since the channels don't correspond to Flutter...
    DartSdkUpdateOption.setDartSdkUpdateOption(DartSdkUpdateOption.DoNotCheck);

    // Update the list of known sdk paths.
    FlutterSdkUtil.updateKnownSdkPaths(flutterSdkPath);

    // Fire events for a Flutter SDK change, which updates the UI.
    FlutterSdkManager.getInstance().checkForFlutterSdkChange();
  }
}

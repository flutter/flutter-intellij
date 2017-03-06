/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.util.PubspecYamlUtil;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fields used when launching an app using the Flutter SDK (non-bazel).
 */
public class SdkFields {
  private @Nullable String filePath;
  private @Nullable String workDir;

  public SdkFields() {}

  /**
   * Creates SDK fields from a Dart file containing a main method.
   *
   * <p>(Automatically chooses the working directory.)
   */
  public SdkFields(VirtualFile launchFile, Project project) {
    filePath = launchFile.getPath();
    workDir = suggestWorkDirFromLaunchFile(launchFile, project).getPath();
  }

  @Nullable
  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(final @Nullable String path) {
    filePath = path;
  }

  @Nullable
  public String getWorkingDirectory() {
    return workDir;
  }

  public void setWorkingDirectory(final @Nullable String dir) {
    workDir = dir;
  }

  /**
   * Reports any errors that the user should correct.
   *
   * <p>This will be called while the user is typing; see RunConfiguration.checkConfiguration.
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  void checkRunnable(final @NotNull Project project) throws RuntimeConfigurationError {
    checkSdk(project);
    final VirtualFile file = checkLaunchFile(filePath);
    chooseWorkDir(file, project);
  }

  /**
   * Chooses the work directory to launch with.
   */
  VirtualFile chooseWorkDir(VirtualFile launchFile, Project project) throws RuntimeConfigurationError {
    // Work directory is optional; if not set we must infer it.
    if (!StringUtil.isEmptyOrSpaces(workDir)) {
      return checkWorkDir(workDir);
    } else {
      return suggestWorkDirFromLaunchFile(launchFile, project);
    }
  }

  SdkFields copy() {
    final SdkFields copy = new SdkFields();
    copy.setFilePath(filePath);
    copy.setWorkingDirectory(workDir);
    return copy;
  }

  /**
   * Chooses a suitable working directory based on the user's selected Dart file.
   */
  static @NotNull VirtualFile suggestWorkDirFromLaunchFile(@NotNull VirtualFile launchFile, @NotNull Project project) {
    // Default to pubspec's directory if available.
    final VirtualFile pubspec = PubspecYamlUtil.findPubspecYamlFile(project, launchFile);
    if (pubspec != null) {
      final VirtualFile parent = pubspec.getParent();
      if (parent != null) return parent;
    }

    // Otherwise use the closest directory.
    // (It should not be a directory, but handle that case anyway.)
    return launchFile.isDirectory() ? launchFile : launchFile.getParent();
  }

  static DartSdk checkSdk(@NotNull Project project) throws RuntimeConfigurationError {
    // TODO(skybrian) shouldn't this be flutter SDK?

    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }
    return sdk;
  }

  static @NotNull VirtualFile checkLaunchFile(String path) throws RuntimeConfigurationError {
    // TODO(skybrian) also check that it's a Flutter app and contains main.

    if (StringUtil.isEmptyOrSpaces(path)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("path.to.dart.file.not.set"));
    }

    final VirtualFile dartFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (dartFile == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.file.not.found", FileUtil.toSystemDependentName(path)));
    }

    if (dartFile.getFileType() != DartFileType.INSTANCE) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("not.a.dart.file", FileUtil.toSystemDependentName(path)));
    }

    return dartFile;
  }

  static VirtualFile checkWorkDir(String dir) throws RuntimeConfigurationError {
    final VirtualFile workDir = LocalFileSystem.getInstance().findFileByPath(dir);
    if (workDir == null || !workDir.isDirectory()) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("work.dir.does.not.exist", FileUtil.toSystemDependentName(dir)));
    }
    return workDir;
  }
}

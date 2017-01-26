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

public class FlutterRunnerParameters implements Cloneable {
  public static String suggestDartWorkingDir(@NotNull Project project, @NotNull VirtualFile dartFileOrFolder) {
    final VirtualFile pubspec = PubspecYamlUtil.findPubspecYamlFile(project, dartFileOrFolder);
    if (pubspec != null) {
      final VirtualFile parent = pubspec.getParent();
      if (parent != null) {
        return parent.getPath();
      }
    }

    return dartFileOrFolder.isDirectory() ? dartFileOrFolder.getPath() : dartFileOrFolder.getParent().getPath();
  }

  // Regular launch parameters.
  private @Nullable String myFilePath;
  private @Nullable String myWorkingDirectory;

  // Bazel launch parameters.
  private @Nullable String myLaunchingScript;
  private @Nullable String myAdditionalArgs;
  private @Nullable String myBazelTarget;

  @NotNull
  public String computeProcessWorkingDirectory(@NotNull final Project project) {
    if (!StringUtil.isEmptyOrSpaces(myWorkingDirectory)) return myWorkingDirectory;

    try {
      return suggestDartWorkingDir(project, getDartFileOrDirectory());
    }
    catch (RuntimeConfigurationError error) {
      return "";
    }
  }

  @Nullable
  public String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(final @Nullable String filePath) {
    myFilePath = filePath;
  }

  @Nullable
  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public void setWorkingDirectory(final @Nullable String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  @Nullable
  public String getLaunchingScript() {
    return myLaunchingScript;
  }

  public void setLaunchingScript(@Nullable String launchingScript) {
    myLaunchingScript = launchingScript;
  }

  @Nullable
  public String getAdditionalArgs() {
    return myAdditionalArgs;
  }

  public void setAdditionalArgs(@Nullable String additionalArgs) {
    myAdditionalArgs = additionalArgs;
  }

  @Nullable
  public String getBazelTarget() {
    return myBazelTarget;
  }

  public void setBazelTarget(@Nullable String bazelTarget) {
    myBazelTarget = bazelTarget;
  }

  @NotNull
  public VirtualFile getDartFile() throws RuntimeConfigurationError {
    final VirtualFile dartFile = getDartFileOrDirectory();
    if (dartFile.isDirectory()) {
      assert myFilePath != null;
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.file.not.found", FileUtil.toSystemDependentName(myFilePath)));
    }
    return dartFile;
  }

  @NotNull
  VirtualFile getBestContextFile() throws RuntimeConfigurationError {
    if (!StringUtil.isEmptyOrSpaces(myFilePath)) {
      return getDartFile();
    }
    else if (!StringUtil.isEmptyOrSpaces(myWorkingDirectory)) {
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myWorkingDirectory);
      if (file == null) {
        throw new RuntimeConfigurationError("unable to determine the working directory");
      }
      else {
        return file;
      }
    }
    else {
      throw new RuntimeConfigurationError("unable to determine the working directory");
    }
  }

  @NotNull
  public VirtualFile getDartFileOrDirectory() throws RuntimeConfigurationError {
    if (StringUtil.isEmptyOrSpaces(myFilePath)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("path.to.dart.file.not.set"));
    }

    final VirtualFile dartFile = LocalFileSystem.getInstance().findFileByPath(myFilePath);
    if (dartFile == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.file.not.found", FileUtil.toSystemDependentName(myFilePath)));
    }

    if (dartFile.getFileType() != DartFileType.INSTANCE && !dartFile.isDirectory()) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("not.a.dart.file.or.directory", FileUtil.toSystemDependentName(myFilePath)));
    }

    return dartFile;
  }

  @Nullable
  public VirtualFile getDartFileOrDirectoryNoThrow() {
    if (StringUtil.isEmptyOrSpaces(myFilePath)) {
      return null;
    }

    try {
      return getDartFileOrDirectory();
    }
    catch (RuntimeConfigurationError ignore) {
      return null;
    }
  }

  public void checkForBazelLaunch(final @NotNull Project project) throws RuntimeConfigurationError {
    // check sdk
    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }

    // check launcher script
    if (StringUtil.isEmptyOrSpaces(myLaunchingScript)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noLaunchingScript"));
    }

    final VirtualFile scriptFile = LocalFileSystem.getInstance().findFileByPath(myLaunchingScript);
    if (scriptFile == null) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("flutter.run.bazel.launchingScriptNotFound", FileUtil.toSystemDependentName(myLaunchingScript)));
    }

    // check bazel target
    if (StringUtil.isEmptyOrSpaces(myBazelTarget)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noTargetSet"));
    }

    // check cwd param
    if (StringUtil.isEmptyOrSpaces(myWorkingDirectory)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noWorkingDirectorySet"));
    }

    final VirtualFile workDir = LocalFileSystem.getInstance().findFileByPath(myWorkingDirectory);
    if (workDir == null || !workDir.isDirectory()) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("work.dir.does.not.exist", FileUtil.toSystemDependentName(myWorkingDirectory)));
    }
  }

  public void checkForFilesLaunch(final @NotNull Project project) throws RuntimeConfigurationError {
    // check sdk
    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }

    // check main dart file
    getDartFileOrDirectory();

    // check working directory
    if (!StringUtil.isEmptyOrSpaces(myWorkingDirectory)) {
      final VirtualFile workDir = LocalFileSystem.getInstance().findFileByPath(myWorkingDirectory);
      if (workDir == null || !workDir.isDirectory()) {
        throw new RuntimeConfigurationError(
          FlutterBundle.message("work.dir.does.not.exist", FileUtil.toSystemDependentName(myWorkingDirectory)));
      }
    }
  }

  @Override
  public FlutterRunnerParameters clone() {
    try {
      return (FlutterRunnerParameters)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}

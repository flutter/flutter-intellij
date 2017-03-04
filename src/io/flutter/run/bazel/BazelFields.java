/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The fields in a Bazel run configuration.
 */
public class BazelFields {

  private @Nullable String workDir;
  private @Nullable String launchScript;
  private @Nullable String additionalArgs;
  private @Nullable String bazelTarget;

  @Nullable
  public String getWorkingDirectory() {
    return workDir;
  }

  public void setWorkingDirectory(final @Nullable String workDir) {
    this.workDir = workDir;
  }

  @Nullable
  public String getLaunchingScript() {
    return launchScript;
  }

  public void setLaunchingScript(@Nullable String launchScript) {
    this.launchScript = launchScript;
  }

  @Nullable
  public String getAdditionalArgs() {
    return additionalArgs;
  }

  public void setAdditionalArgs(@Nullable String additionalArgs) {
    this.additionalArgs = additionalArgs;
  }

  @Nullable
  public String getBazelTarget() {
    return bazelTarget;
  }

  public void setBazelTarget(@Nullable String bazelTarget) {
    this.bazelTarget = bazelTarget;
  }

  BazelFields copy() {
    final BazelFields copy = new BazelFields();
    copy.setWorkingDirectory(workDir);
    copy.setLaunchingScript(launchScript);
    copy.setBazelTarget(bazelTarget);
    copy.setAdditionalArgs(additionalArgs);
    return copy;
  }

  /**
   * Reports any errors that the user should correct.
   *
   * <p>This will be called while the user is typing; see RunConfiguration.checkConfiguration.
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  void checkRunnable(final @NotNull Project project) throws RuntimeConfigurationError {
    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }

    // check launcher script
    if (StringUtil.isEmptyOrSpaces(launchScript)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noLaunchingScript"));
    }

    final VirtualFile scriptFile = LocalFileSystem.getInstance().findFileByPath(launchScript);
    if (scriptFile == null) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("flutter.run.bazel.launchingScriptNotFound", FileUtil.toSystemDependentName(launchScript)));
    }

    // check bazel target
    if (StringUtil.isEmptyOrSpaces(bazelTarget)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noTargetSet"));
    }

    // The working directory is optional, provided there is a Workspace.
    if (chooseWorkDir(project) == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noWorkingDirectorySet"));
    }
  }

  /**
   * Chooses the work directory to launch with.
   */
  @Nullable VirtualFile chooseWorkDir(@NotNull final Project project) {
    // Prefer user's selection.
    if (!StringUtil.isEmptyOrSpaces(workDir)) {
      final VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(workDir);
      if (dir == null || !dir.isDirectory()) return null;
      return dir;
    }

    // Default to Workspace root.
    // TODO(skybrian) maybe we should extract the directory from the Bazel target to get closer?
    final Workspace w = WorkspaceCache.getInstance(project).getNow();
    if (w != null) return w.getRoot();

    // None found.
    return null;
  }
}

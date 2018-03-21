/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.MainFile;
import io.flutter.run.bazel.BazelFields;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The fields in a Bazel test run configuration, see {@link io.flutter.run.test.TestFields}.
 */
public class BazelTestFields {

  @Nullable private String entryFile;
  @Nullable private String launchScript;
  @Nullable private String bazelTarget;

  BazelTestFields() {
  }

  /**
   * Copy constructor
   */
  private BazelTestFields(@NotNull final BazelTestFields original) {
    entryFile = original.entryFile;
    launchScript = original.launchScript;
    bazelTarget = original.bazelTarget;
  }

  /**
   * Create non-template from template.
   */
  private BazelTestFields(@NotNull final BazelTestFields template, @NotNull final Workspace workspace) {
    this(template);
    if (StringUtil.isEmptyOrSpaces(launchScript)) {
      launchScript = workspace.getLaunchScript();
      if (launchScript != null && !launchScript.startsWith("/")) {
        launchScript = workspace.getRoot().getPath() + "/" + launchScript;
      }
    }
  }

  /**
   * The file containing the main function that starts the Flutter app.
   */
  @Nullable
  public String getEntryFile() {
    return entryFile;
  }

  public void setEntryFile(@Nullable final String entryFile) {
    this.entryFile = entryFile;
  }

  /**
   * Returns the file or directory containing the tests to run, or null if it doesn't exist.
   */
  @Nullable
  public VirtualFile getFile() {
    final String path = getEntryFile();
    if (path == null) return null;
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  /**
   * Present only for deserializing old run configs.
   */
  @SuppressWarnings("SameReturnValue")
  @Deprecated
  public String getWorkingDirectory() {
    return null;
  }

  /**
   * Used only for deserializing old run configs.
   */
  @Deprecated
  public void setWorkingDirectory(@Nullable final String workDir) {
    if (entryFile == null && workDir != null) {
      entryFile = workDir + "/lib/main.dart";
    }
  }

  @Nullable
  public String getLaunchingScript() {
    return launchScript;
  }

  public void setLaunchingScript(@Nullable final String launchScript) {
    this.launchScript = launchScript;
  }

  @Nullable
  public String getBazelTarget() {
    return bazelTarget;
  }

  public void setBazelTarget(@Nullable final String bazelTarget) {
    this.bazelTarget = bazelTarget;
  }

  @NotNull
  BazelTestFields copy() {
    return new BazelTestFields(this);
  }

  @NotNull
  BazelTestFields copyTemplateToNonTemplate(@NotNull final Project project) {
    final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
    if (workspace == null) return new BazelTestFields(this);
    return new BazelTestFields(this, workspace);
  }

  /**
   * Reports an error in the run config that the user should correct.
   * <p>
   * This will be called while the user is typing into a non-template run config.
   * (See RunConfiguration.checkConfiguration.)
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  void checkRunnable(@NotNull final Project project) throws RuntimeConfigurationError {
    BazelFields.checkRunnable(project, getEntryFile(), getLaunchingScript(), getBazelTarget());
  }

  /**
   * Starts running the tests.
   */
  @NotNull
  ProcessHandler run(@NotNull final Project project, @NotNull final RunMode mode) throws ExecutionException {
    return new OSProcessHandler(getLaunchCommand(project, mode));
  }

  /**
   * Returns the command to use to launch the Flutter app. (Via running the Bazel target.)
   */
  @NotNull
  GeneralCommandLine getLaunchCommand(@NotNull final Project project,
                                      @NotNull final RunMode mode)
    throws ExecutionException {
    try {
      checkRunnable(project);
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final VirtualFile appDir = MainFile.verify(entryFile, project).get().getAppDir();

    final String launchingScript = getLaunchingScript();
    assert launchingScript != null; // already checked

    final String target = getBazelTarget();
    assert target != null; // already checked

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(appDir.getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    commandLine.addParameter(target);

    if (mode == RunMode.DEBUG) {
      commandLine.addParameters("--", "--enable-debugging");
    }
    return commandLine;
  }
}

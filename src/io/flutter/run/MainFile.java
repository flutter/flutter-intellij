/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.pub.PubRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The location of a Dart file containing a main() method for launching a Flutter app.
 * <p>
 * It has been checked for errors, but the check might not have succeeded.
 */
public class MainFile {

  @NotNull
  private final VirtualFile file;

  @NotNull
  private final VirtualFile appDir;

  private MainFile(@NotNull VirtualFile file, @NotNull VirtualFile appDir) {
    this.file = file;
    this.appDir = appDir;
  }

  /**
   * Returns the Dart file containing main.
   */
  @NotNull
  public VirtualFile getFile() {
    return file;
  }

  /**
   * Returns the closest ancestor directory containing a pubspec.yaml, BUILD, or packages meta-data file.
   */
  @NotNull
  public VirtualFile getAppDir() {
    return appDir;
  }

  /**
   * Verifies that the given path points to an entrypoint file within a Flutter app.
   * <p>
   * If there is an error, {@link Result#canLaunch} will return false and the error is available via {@link Result#getError}
   */
  @NotNull
  public static MainFile.Result verify(@Nullable String path, @Nullable Project project) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      throw new IllegalStateException("need read access");
    }

    if (project == null) {
      return error("Project is not set.");
    }

    if (StringUtil.isEmptyOrSpaces(path)) {
      return error(FlutterBundle.message("entrypoint.not.set"));
    }

    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) {
      return error(FlutterBundle.message("entrypoint.not.found", FileUtil.toSystemDependentName(path)));
    }
    if (!FlutterUtils.isDartFile(file)) {
      return error(FlutterBundle.message("entrypoint.not.dart"));
    }

    final PsiFile psi = PsiManager.getInstance(project).findFile(file);
    if (!(psi instanceof DartFile dart)) {
      return error(FlutterBundle.message("entrypoint.not.dart"));
    }

    if (DartResolveUtil.getMainFunction(dart) == null) {
      return error(FlutterBundle.message("main.not.in.entrypoint"));
    }

    if (!inProject(file, project)) {
      return error(FlutterBundle.message("entrypoint.not.in.project"));
    }

    final VirtualFile dir = findAppDir(file, project);
    if (dir == null) {
      return error(FlutterBundle.message("entrypoint.not.in.app.dir"));
    }

    return new MainFile.Result(new MainFile(file, dir), null);
  }

  @Nullable
  private static VirtualFile findAppDir(@Nullable VirtualFile file, @NotNull Project project) {
    if (WorkspaceCache.getInstance(project).isBazel()) {
      final Workspace workspace = WorkspaceCache.getInstance(project).get();
      assert (workspace != null);
      return workspace.getRoot();
    }

    for (VirtualFile candidate = file; inProject(candidate, project); candidate = candidate.getParent()) {
      if (isAppDir(candidate, project)) return candidate;
    }
    return null;
  }

  private static boolean isAppDir(@NotNull VirtualFile dir, @NotNull Project project) {
    assert (!WorkspaceCache.getInstance(project).isBazel());
    return dir.isDirectory() && (
      dir.findChild(PubRoot.PUBSPEC_YAML) != null ||
      dir.findChild(PubRoot.DOT_DART_TOOL) != null ||
      dir.findChild(PubRoot.DOT_PACKAGES) != null
    );
  }

  private static boolean inProject(@Nullable VirtualFile file, @NotNull Project project) {
    // Do a speedy check for containment over accessing the file index (which we did historically)
    // but is very slow and unacceptably blocks the UI thread.
    // See: https://github.com/flutter/flutter-intellij/issues/8089
    return file != null && BaseProjectDirectories.getInstance(project).contains(file);
  }

  private static MainFile.Result error(@NotNull String message) {
    return new MainFile.Result(null, message);
  }

  /**
   * The result of {@link #verify}; either a MainFile or an error.
   */
  public static class Result {
    @Nullable
    private final MainFile file;

    @Nullable
    private final String error;

    private Result(@Nullable MainFile file, @Nullable String error) {
      assert (file == null || error == null);
      assert (file != null || error != null);
      this.file = file;
      this.error = error;
    }

    /**
     * Returns true if the Flutter app can be launched.
     * <p>
     * If false, the error can be found by calling {@link #getError}.
     */
    public boolean canLaunch() {
      return error == null;
    }

    /**
     * Returns the error message to display if this file is not launchable.
     */
    @NotNull
    public String getError() {
      if (error == null) {
        throw new IllegalStateException("called getError when there is no error");
      }
      return error;
    }

    /**
     * Unwraps the MainFile. Valid only if there's not an error.
     */
    public MainFile get() {
      if (file == null) {
        throw new IllegalStateException("called getLaunchable when there is an error: " + error);
      }
      return file;
    }
  }
}

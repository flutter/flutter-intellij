/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.psi.DartImportStatement;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import io.flutter.FlutterBundle;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

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

  private final boolean flutterImports;

  private MainFile(@NotNull VirtualFile file, @NotNull VirtualFile appDir, boolean flutterImports) {
    this.file = file;
    this.appDir = appDir;
    this.flutterImports = flutterImports;
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
   * Returns true if the file has any direct flutter imports.
   */
  public boolean hasFlutterImports() {
    return flutterImports;
  }

  /**
   * Verifies that the given path points to an entrypoint file within a Flutter app.
   * <p>
   * If there is an error, {@link Result#canLaunch} will return false and the error is available via {@link Result#getError}
   */
  @NotNull
  public static MainFile.Result verify(@Nullable String path, Project project) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      throw new IllegalStateException("need read access");
    }

    if (StringUtil.isEmptyOrSpaces(path)) {
      return error(FlutterBundle.message("entrypoint.not.set"));
    }

    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) {
      return error(FlutterBundle.message("entrypoint.not.found", FileUtil.toSystemDependentName(path)));
    }
    if (file.getFileType() != DartFileType.INSTANCE) {
      return error(FlutterBundle.message("entrypoint.not.dart"));
    }

    final PsiFile psi = PsiManager.getInstance(project).findFile(file);
    if (!(psi instanceof DartFile)) {
      return error(FlutterBundle.message("entrypoint.not.dart"));
    }
    final DartFile dart = (DartFile)psi;

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

    final boolean hasFlutterImports = findImportUrls(dart).anyMatch((url) -> url.startsWith("package:flutter/"));

    return new MainFile.Result(new MainFile(file, dir, hasFlutterImports), null);
  }

  @Nullable
  private static VirtualFile findAppDir(@Nullable VirtualFile file, @NotNull Project project) {
    if (WorkspaceCache.getInstance(project).isBazel()) {
      final Workspace workspace = WorkspaceCache.getInstance(project).get();
      assert(workspace != null);
      return workspace.getRoot();
    }

    for (VirtualFile candidate = file; inProject(candidate, project); candidate = candidate.getParent()) {
      if (isAppDir(candidate, project)) return candidate;
    }
    return null;
  }

  private static boolean isAppDir(@NotNull VirtualFile dir, @NotNull Project project) {
    assert(!WorkspaceCache.getInstance(project).isBazel());
    return dir.isDirectory() && (
      dir.findChild("pubspec.yaml") != null ||
      dir.findChild(".dart_tool") != null ||
      dir.findChild(".packages") != null
    );
  }

  private static boolean inProject(@Nullable VirtualFile file, @NotNull Project project) {
    return file != null && ProjectRootManager.getInstance(project).getFileIndex().isInContent(file);
  }

  /**
   * Returns the import URL's in a Dart file.
   */
  @NotNull
  private static Stream<String> findImportUrls(@NotNull DartFile file) {
    final DartImportStatement[] imports = PsiTreeUtil.getChildrenOfType(file, DartImportStatement.class);
    if (imports == null) return Stream.empty();

    return Arrays.stream(imports).map(DartImportStatement::getUriString);
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

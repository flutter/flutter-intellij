/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.pub;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A snapshot of the root directory of a pub package.
 * <p>
 * That is, a directory containing (at a minimum) a pubspec.yaml file.
 */
public class PubRoot {
  @NotNull
  private final VirtualFile root;

  @NotNull
  private final VirtualFile pubspec;

  @Nullable
  private final VirtualFile packages;

  @Nullable
  private final VirtualFile lib;

  private PubRoot(@NotNull VirtualFile root, @NotNull VirtualFile pubspec, @Nullable VirtualFile packages, @Nullable VirtualFile lib) {
    this.root = root;
    this.pubspec = pubspec;
    this.packages = packages;
    this.lib = lib;
  }

  /**
   * Returns the unique pub root for a project.
   * <p>
   * If there is more than one, returns null.
   * <p>
   * Refreshes the returned pubroot's directory. (Not any others.)
   */
  @Nullable
  public static PubRoot forProjectWithRefresh(@NotNull Project project) {
    final List<PubRoot> roots = PubRoots.forProject(project);
    if (roots.size() != 1) {
      return null;
    }
    return roots.get(0).refresh();
  }

  /**
   * Returns the unique pub root for a module.
   * <p>
   * If there is more than one, returns null.
   * <p>
   * Refreshes the returned pubroot's directory. (Not any others.)
   */
  @Nullable
  public static PubRoot forModuleWithRefresh(@NotNull Module module) {
    final List<PubRoot> roots = PubRoots.forModule(module);
    if (roots.size() != 1) {
      return null;
    }
    return roots.get(0).refresh();
  }

  /**
   * Returns the most appropriate pub root for a PsiFile.
   * <p>
   * If the file is within a content root and it contains a pubspec.yaml file, use that one.
   * Otherwise, use the pubspec.yaml file for the project (if unique).
   * <p>
   * Based on the filesystem cache; doesn't refresh anything.
   */
  @Nullable
  public static PubRoot forPsiFile(@NotNull PsiFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
    final VirtualFile root = index.getContentRootForFile(file.getVirtualFile());
    if (root != null) {
      return forDirectory(root);
    }

    // Fall back to using the unique pub root for this project (if any).
    final List<PubRoot> roots = PubRoots.forProject(file.getProject());
    if (roots.size() != 1) {
      return null;
    }

    return roots.get(0);
  }

  /**
   * Returns the PubRoot for a directory, provided it contains a pubspec.yaml file.
   * <p>
   * Otherwise returns null.
   * <p>
   * (The existence check is based on the filesystem cache; doesn't refresh anything.)
   */
  @Nullable
  public static PubRoot forDirectory(@NotNull VirtualFile dir) {
    if (!dir.isDirectory()) {
      return null;
    }

    final VirtualFile pubspec = dir.findChild("pubspec.yaml");
    if (pubspec == null || !pubspec.exists() || pubspec.isDirectory()) {
      return null;
    }

    VirtualFile packages = dir.findChild(".packages");
    if (packages == null || !packages.exists() || packages.isDirectory()) {
      packages = null;
    }

    VirtualFile lib = dir.findChild("lib");
    if (lib == null || !lib.exists() || !lib.isDirectory()) {
      lib = null;
    }

    return new PubRoot(dir, pubspec, packages, lib);
  }

  /**
   * Returns the PubRoot for a directory, provided it contains a pubspec.yaml file.
   * <p>
   * Refreshes the directory and the lib directory (if present). Returns null if not found.
   */
  @Nullable
  public static PubRoot forDirectoryWithRefresh(@NotNull VirtualFile dir) {
    // Ensure file existence and timestamps are up to date.
    dir.refresh(false, false);

    final PubRoot root = forDirectory(dir);
    if (root == null) return null;

    final VirtualFile lib = root.getLib();
    if (lib != null) lib.refresh(false, false);
    return root;
  }

  /**
   * Refreshes the pubroot and lib directories and returns an up-to-date snapshot.
   * <p>
   * Returns null if the directory or pubspec file is no longer there.
   */
  @Nullable
  public PubRoot refresh() {
    return forDirectoryWithRefresh(root);
  }

  @NotNull
  public VirtualFile getRoot() {
    return root;
  }

  @NotNull
  public VirtualFile getPubspec() {
    return pubspec;
  }

  /**
   * Returns true if the pubspec declares a flutter dependency.
   */
  public boolean declaresFlutter() {
    try {
      final String contents = new String(pubspec.contentsToByteArray(true /* cache contents */));
      return FLUTTER_SDK_DEP.matcher(contents).find();
    }
    catch (IOException e) {
      return false;
    }
  }

  @Nullable
  public VirtualFile getPackages() {
    return packages;
  }

  /**
   * Returns true if the packages file is up to date.
   */
  public boolean hasUpToDatePackages() {
    return packages != null && pubspec.getTimeStamp() < packages.getTimeStamp();
  }

  @Nullable
  public VirtualFile getLib() {
    return lib;
  }

  /**
   * Returns lib/main.dart if it exists.
   */
  @Nullable
  public VirtualFile getLibMain() {
    return lib == null ? null : lib.findChild("main.dart");
  }

  public static boolean isPubspec(@NotNull VirtualFile file) {
    return !file.isDirectory() && file.getName().equals("pubspec.yaml");
  }

  private static final Pattern FLUTTER_SDK_DEP = Pattern.compile(".*sdk:\\s*flutter"); //NON-NLS
}

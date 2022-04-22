/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.pub;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Cache the information computed from pubspecs in the project.
 */
public class PubRootCache {
  @NotNull
  public static PubRootCache getInstance(@NotNull final Project project) {
    return Objects.requireNonNull(project.getService(PubRootCache.class));
  }

  @NotNull final Project project;

  private final Map<VirtualFile, PubRoot> cache = new HashMap<>();

  private PubRootCache(@NotNull final Project project) {
    this.project = project;
  }

  @Nullable
  public PubRoot getRoot(@NotNull PsiFile psiFile) {
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) {
      return null;
    }

    return getRoot(file);
  }

  @Nullable
  public PubRoot getRoot(VirtualFile file) {
    file = findPubspecDir(file);
    if (file == null) {
      return null;
    }

    PubRoot root = cache.get(file);

    if (root == null) {
      cache.put(file, PubRoot.forDirectory(file));
      root = cache.get(file);
    }

    return root;
  }

  @NotNull
  public List<PubRoot> getRoots(Module module) {
    final List<PubRoot> result = new ArrayList<>();

    for (VirtualFile dir : ModuleRootManager.getInstance(module).getContentRoots()) {
      PubRoot root = cache.get(dir);

      if (root == null) {
        cache.put(dir, PubRoot.forDirectory(dir));
        root = cache.get(dir);
      }

      if (root != null) {
        result.add(root);
      }
    }

    return result;
  }

  @NotNull
  public List<PubRoot> getRoots(@NotNull Project project) {
    final List<PubRoot> result = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      result.addAll(getRoots(module));
    }
    return result;
  }

  @Nullable
  private VirtualFile findPubspecDir(VirtualFile file) {
    if (file == null) {
      return null;
    }

    if (file.isDirectory()) {
      final VirtualFile pubspec = file.findChild("pubspec.yaml");
      if (pubspec != null && pubspec.exists() && !pubspec.isDirectory()) {
        return file;
      }
    }

    return findPubspecDir(file.getParent());
  }
}

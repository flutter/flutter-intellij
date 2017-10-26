/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.pub;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Queries returning a list of {@link PubRoot} directories.
 */
public class PubRoots {
  // Not instantiable.
  private PubRoots() {
  }

  /**
   * Returns a PubRoot for each of the module's content roots that contains a pubspec.yaml file.
   * <p>
   * (Based on the filesystem cache; doesn't refresh anything.)
   */
  @NotNull
  public static List<PubRoot> forModule(@NotNull Module module) {
    final List<PubRoot> result = new ArrayList<>();
    for (VirtualFile dir : ModuleRootManager.getInstance(module).getContentRoots()) {
      final PubRoot root = PubRoot.forDirectory(dir);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  /**
   * Returns a PubRoot for each of the project's content roots that contains a pubspec.yaml file.
   * <p>
   * (Based on the filesystem cache; doesn't refresh anything.)
   */
  @NotNull
  public static List<PubRoot> forProject(@NotNull Project project) {
    final List<PubRoot> result = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      result.addAll(forModule(module));
    }
    return result;
  }
}

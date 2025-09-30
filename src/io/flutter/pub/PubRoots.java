/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.pub;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.utils.OpenApiUtils;
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
    if (module.isDisposed()) return result;

    for (VirtualFile dir : OpenApiUtils.getContentRoots(module)) {
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
    if (project.isDisposed()) return result;

    for (Module module : OpenApiUtils.getModules(project)) {
      result.addAll(forModule(module));
    }
    return result;
  }
}

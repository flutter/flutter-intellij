/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.util;


import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.module.FlutterModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ModuleUtil {

  public static  @Nullable VirtualFile getProjectContentRoot(@NotNull Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    return roots.length > 0 ? roots[0] : null;
  }

  public static @Nullable String getProjectBasePath(Project project) {
    Collection<Module> modules = com.intellij.openapi.module.ModuleUtil.getModulesOfType(project, FlutterModuleType.getInstance());
    for (Module module : modules) {
      VirtualFile contentRoot = getProjectContentRoot(module);
      if (contentRoot != null && contentRoot.exists()) {
        return contentRoot.getPath();
      }
    }
    return null;
  }
}

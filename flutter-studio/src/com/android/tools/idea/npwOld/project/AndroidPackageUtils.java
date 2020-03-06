/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npwOld.project;

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A handful of utility methods useful for suggesting package names when creating new files inside
 * an Android project.
 */
public final class AndroidPackageUtils {

  private AndroidPackageUtils() {
  }

  /**
   * Return the top-level package associated with this project.
   */
  @NotNull
  public static String getPackageForApplication(@NotNull AndroidFacet androidFacet) {
    AndroidModel androidModel = androidFacet.getConfiguration().getModel();
    assert androidModel != null;
    return androidModel.getApplicationId();
  }

  /**
   * Return the package associated with the target directory.
   */
  @NotNull
  public static String getPackageForPath(@NotNull AndroidFacet androidFacet,
                                         @NotNull List<NamedModuleTemplate> moduleTemplates,
                                         @NotNull VirtualFile targetDirectory) {
    if (!moduleTemplates.isEmpty()) {
      Module module = androidFacet.getModule();
      File srcDirectory = moduleTemplates.get(0).getPaths().getSrcDirectory(null);
      if (srcDirectory != null) {
        // We generate a package name relative to the source root, but if the target path is not under the source root, we should just
        // fall back to the default application package.
        Path srcPath = Paths.get(srcDirectory.getPath()).toAbsolutePath();
        Path targetPath = Paths.get(targetDirectory.getPath()).toAbsolutePath();
        if (targetPath.startsWith(srcPath)) {
          ProjectRootManager projectManager = ProjectRootManager.getInstance(module.getProject());
          String suggestedPackage = projectManager.getFileIndex().getPackageNameByDirectory(targetDirectory);
          if (suggestedPackage != null && !suggestedPackage.isEmpty()) {
            return suggestedPackage;
          }
        }
      }
    }

    return getPackageForApplication(androidFacet);
  }

  /**
   * Convenience method to get {@link NamedModuleTemplate}s from the current project.
   */
  @NotNull
  public static List<NamedModuleTemplate> getModuleTemplates(@NotNull AndroidFacet facet, @Nullable VirtualFile targetDirectory) {
    return ProjectSystemUtil.getModuleSystem(facet.getModule()).getModuleTemplates(targetDirectory);
  }
}

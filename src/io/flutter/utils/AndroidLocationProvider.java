/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

// Copied from GradleModelSource.ResolvedConfigurationFileLocationProviderImpl
// This file must be ignored in pre-4.1 builds.
public class AndroidLocationProvider implements BuildModelContext.ResolvedConfigurationFileLocationProvider {
  @Nullable
  @Override
  public VirtualFile getGradleBuildFile(@NotNull Module module) {
    // TODO(helin24): Delete this code (and potentially related code) if commenting out has no negative impact on Android editing.
    // I believe this is to make gradle files show up nicely when a flutter project is opened, but this functionality already does not work
    // and is not needed if we are recommending users edit Android files in a separate project window.

    //GradleModuleModel moduleModel = GradleProjectSystemUtil.getGradleModuleModel(module);
    //if (moduleModel != null) {
    //  return moduleModel.getBuildFile();
    //}
    return null;
  }

  @Nullable
  @Override
  public @SystemIndependent String getGradleProjectRootPath(@NotNull Module module) {
    return AndroidProjectRootUtil.getModuleDirPath(module);
  }

  @Nullable
  @Override
  public @SystemIndependent String getGradleProjectRootPath(@NotNull Project project) {
    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir == null) return null;
    return projectDir.getPath();
  }
}

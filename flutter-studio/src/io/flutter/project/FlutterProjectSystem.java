/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class FlutterProjectSystem implements AndroidProjectSystem {
  @NotNull final private GradleProjectSystem gradleProjectSystem;

  public FlutterProjectSystem(Project project) {
    gradleProjectSystem = new GradleProjectSystem(project);
  }

  @Nullable
  @Override
  public VirtualFile getDefaultApkFile() {
    return gradleProjectSystem.getDefaultApkFile();
  }

  @NotNull
  @Override
  public Path getPathToAapt() {
    return gradleProjectSystem.getPathToAapt();
  }

  @Override
  public void buildProject() {
    // flutter build ?
  }

  @Override
  public boolean allowsFileCreation() {
    return true; // Enable File>New>New Module
  }

  @NotNull
  @Override
  public AndroidModuleSystem getModuleSystem(@NotNull Module module) {
    return gradleProjectSystem.getModuleSystem(module);
  }

  @Override
  public boolean upgradeProjectToSupportInstantRun() {
    return false; // Already done.
  }

  @NotNull
  @Override
  public String mergeBuildFiles(@NotNull String dependencies,
                                @NotNull String destinationContents,
                                @Nullable String supportLibVersionFilter) {
    return gradleProjectSystem.mergeBuildFiles(dependencies, destinationContents, supportLibVersionFilter);
  }

  @NotNull
  @Override
  public ProjectSystemSyncManager getSyncManager() {
    return gradleProjectSystem.getSyncManager();
  }
}

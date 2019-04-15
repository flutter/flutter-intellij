/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.LightResourceClassService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import com.intellij.util.ReflectionUtil;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public class FlutterProjectSystem implements AndroidProjectSystem {
  private static final Logger LOG = Logger.getInstance(FlutterProjectSystem.class);
  @NotNull final private GradleProjectSystem gradleProjectSystem;

  public FlutterProjectSystem(Project project) {
    gradleProjectSystem = new GradleProjectSystemProvider(project).getProjectSystem();
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
    FlutterUtils.warn(LOG, "FlutterProjectSystem.buildProject() called but not (properly) implemented.");
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

  @SuppressWarnings("override")
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

  @SuppressWarnings("override")
  @Nullable
  public GradleCoordinate getAvailableDependency(@NotNull GradleCoordinate coordinate, boolean includePreview) {
    //return gradleProjectSystem.getAvailableDependency(coordinate, includePreview);
    // TODO(messick): Delete the remainder of this method and use the previous line once AS 3.1 is no longer supported.
    Method finders = ReflectionUtil.getMethod(gradleProjectSystem.getClass(), "getAvailableDependency");
    if (finders == null) {
      FlutterUtils.warn(LOG, "No method found: GradleProjectSystem.getAvailableDependency()");
      return null;
    }
    try {
      //noinspection unchecked
      return (GradleCoordinate)finders.invoke(gradleProjectSystem, coordinate, includePreview);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
  }

  @SuppressWarnings("override")
  @NotNull
  public Collection<PsiElementFinder> getPsiElementFinders() {
    Method finders = ReflectionUtil.getMethod(gradleProjectSystem.getClass(), "getPsiElementFinders");
    if (finders == null) {
      FlutterUtils.warn(LOG, "No method found: GradleProjectSystem.getPsiElementFinders()");
      return Collections.emptyList();
    }
    try {
      //noinspection unchecked
      return (Collection<PsiElementFinder>)finders.invoke(gradleProjectSystem);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
  }

  @SuppressWarnings("override")
  public boolean getAugmentRClasses() {
    return false;
  }

  @NotNull
  @SuppressWarnings("override")
  public LightResourceClassService getLightResourceClassService() {
    return gradleProjectSystem.getLightResourceClassService();
  }
}

/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.LightResourceClassService;
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.SourceProvidersFactory;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ReflectionUtil;
import io.flutter.FlutterUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public String mergeBuildFiles(@NotNull String dependencies,
                                @NotNull String destinationContents,
                                @Nullable String supportLibVersionFilter) {
    Method finders = ReflectionUtil.getMethod(gradleProjectSystem.getClass(), "mergeBuildFiles");
    if (finders == null) {
      FlutterUtils.warn(LOG, "No method found: GradleProjectSystem.getPsiElementFinders()");
      return null;
    }
    try {
      //noinspection unchecked
      return (String)finders.invoke(gradleProjectSystem, dependencies, destinationContents, supportLibVersionFilter);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
  }

  @NotNull
  @Override
  public ProjectSystemSyncManager getSyncManager() {
    return gradleProjectSystem.getSyncManager();
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

  @NotNull
  @SuppressWarnings("override")
  public LightResourceClassService getLightResourceClassService() {
    return gradleProjectSystem.getLightResourceClassService();
  }

  @NotNull
  @SuppressWarnings("override")
  public SourceProvidersFactory getSourceProvidersFactory() {
    return gradleProjectSystem.getSourceProvidersFactory();
  }

  @NotNull
  @SuppressWarnings("override")
  public Collection<Module> getSubmodules() {
    return gradleProjectSystem.getSubmodules();
  }

  @NotNull
  @SuppressWarnings("override")
  public Collection<AndroidFacet> getAndroidFacetsWithPackageName(@NotNull Project project,
                                                                  @NotNull String packageName,
                                                                  @NotNull GlobalSearchScope scope) {
    return gradleProjectSystem.getAndroidFacetsWithPackageName(project, packageName, scope);
  }

  @NotNull
  public ProjectSystemBuildManager getBuildManager() {
    return gradleProjectSystem.getBuildManager();
  }
}

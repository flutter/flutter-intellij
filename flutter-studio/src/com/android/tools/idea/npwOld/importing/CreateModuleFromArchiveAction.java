/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npwOld.importing;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LargeFileWriteRequestor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps archive in a Gradle module.
 */
public class CreateModuleFromArchiveAction extends WriteCommandAction<Object> {
  @NotNull private Project myProject;
  @NotNull private String myGradlePath;
  @NotNull private File myArchivePath;
  private boolean myMove;
  @Nullable private Module myContainingModule;

  public CreateModuleFromArchiveAction(@NotNull Project project,
                                        @NotNull String gradlePath,
                                        @NotNull String archivePath,
                                        boolean move,
                                        @Nullable Module containingModule) {
    super(project, String.format("create module %1$s", gradlePath));
    myProject = project;
    myGradlePath = gradlePath;
    myArchivePath = new File(archivePath);
    myMove = move;
    myContainingModule = containingModule;
  }

  @VisibleForTesting
  protected static String getBuildGradleText(File jarName) {
    return String.format("configurations.maybeCreate(\"default\")\n" + "artifacts.add(\"default\", file('%1$s'))", jarName.getName());
  }

  private void addDependency(@NotNull Module module, String gradlePath) throws IOException {
    GradleBuildFile buildFile = GradleBuildFile.get(module);
    if (buildFile == null) {
      throw new IOException("Missing " + SdkConstants.FN_BUILD_GRADLE);
    }

    List<BuildFileStatement> dependencies = buildFile.getDependencies();
    List<BuildFileStatement> newDeps = Lists.newArrayListWithCapacity(dependencies.size() + 1);
    File moduleRoot = VfsUtilCore.virtualToIoFile(buildFile.getFile().getParent());
    for (BuildFileStatement dependency : dependencies) {
      BuildFileStatement newDep = filterDependencyStatement((Dependency)dependency, moduleRoot);
      if (newDep != null) {
        newDeps.add(newDep);
      }
    }
    Dependency.Scope scope = Dependency.Scope.getDefaultScope(myProject);
    newDeps.add(new Dependency(scope, Dependency.Type.MODULE, gradlePath));
    buildFile.setValue(BuildFileKey.DEPENDENCIES, newDeps);
  }

  @Nullable
  private Dependency filterDependencyStatement(Dependency dependency, File moduleRoot) {
    Object rawArguments = dependency.data;
    if (dependency.type == Dependency.Type.FILES && rawArguments != null) {
      String[] data = rawArguments instanceof String[] ? (String[])rawArguments : new String[]{rawArguments.toString()};
      ArrayList<String> list = Lists.newArrayListWithCapacity(data.length);
      for (String jarFile : data) {
        File path = new File(jarFile);
        if (!path.isAbsolute()) {
          path = new File(moduleRoot, jarFile);
        }
        if (!FileUtil.filesEqual(path, myArchivePath)) {
          list.add(jarFile);
        }
      }
      if (list.isEmpty()) {
        return null;
      }
      else if (list.size() == 1) {
        return new Dependency(dependency.scope, dependency.type, list.get(0));
      }
      else {
        return new Dependency(dependency.scope, dependency.type, Iterables.toArray(list, String.class));
      }
    }
    return dependency;
  }

  @Override
  protected void run(@NotNull Result<Object> result) throws Throwable {
    File moduleLocation = GradleUtil.getModuleDefaultPath(myProject.getBaseDir(), myGradlePath);
    try {
      VirtualFile moduleRoot = VfsUtil.createDirectoryIfMissing(moduleLocation.getAbsolutePath());
      VirtualFile sourceFile = VfsUtil.findFileByIoFile(myArchivePath, true);
      if (sourceFile != null && moduleRoot != null) {
        LargeFileWriteRequestor requestor = new LargeFileWriteRequestor() { };
        if (myMove) {
          sourceFile.move(requestor, moduleRoot);
        }
        else {
          sourceFile.copy(requestor, moduleRoot, sourceFile.getName());
        }
        VirtualFile buildGradle = moduleRoot.createChildData(this, SdkConstants.FN_BUILD_GRADLE);
        VfsUtil.saveText(buildGradle, getBuildGradleText(myArchivePath));
        GradleSettingsFile.getOrCreate(myProject).addModule(myGradlePath, VfsUtilCore.virtualToIoFile(moduleRoot));
        if (myMove && myContainingModule != null) {
          addDependency(myContainingModule, myGradlePath);
        }
      }
    }
    catch (IOException e) {
      Logger.getInstance(CreateModuleFromArchiveAction.class).error(e);
    }
  }

  @Override
  protected boolean isGlobalUndoAction() {
    return true;
  }

  @Override
  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
  }
}

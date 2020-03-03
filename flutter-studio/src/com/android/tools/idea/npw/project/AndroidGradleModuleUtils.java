/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.SdkConstants;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.Parameter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidGradleModuleUtils {

  /**
   * Convenience method to convert a {@link NamedModuleTemplate} into a {@link SourceProvider}.
   * Note that this target source provider has many fields stubbed out and should
   * be used carefully.
   *
   * TODO: Investigate getting rid of dependencies on {@link SourceProvider} in
   * {@link Parameter#validate} as this may allow us to delete this code
   */
  @NotNull
  public static SourceProvider getSourceProvider(@NotNull NamedModuleTemplate template) {
    return new SourceProviderAdapter(template.getName(), template.getPaths());
  }

  /**
   * Given a file and a project, return the Module corresponding to the containing Gradle project for the file.  If the file is not
   * contained by any project then return null
   */
  @Nullable
  public static Module getContainingModule(File file, Project project) {
    if (project.isDisposed()) return null;
    VirtualFile vFile = VfsUtil.findFileByIoFile(file, false);
    if (vFile == null || vFile.isDirectory()) {
      return null;
    }
    return ProjectFileIndex.getInstance(project).getModuleForFile(vFile, false);
  }

  /**
   * Set the executable bit on the 'gradlew' wrapper script on Mac/Linux
   * On Windows, we use a separate gradlew.bat file which does not need an
   * executable bit.
   *
   * @throws IOException
   */
  public static void setGradleWrapperExecutable(@NotNull File projectRoot) throws IOException {
    if (SystemInfo.isUnix) {
      File gradlewFile = new File(projectRoot, SdkConstants.FN_GRADLE_WRAPPER_UNIX);
      if (!gradlewFile.isFile()) {
        throw new IOException("Could not find gradle wrapper. Command line builds may not work properly.");
      }
      FileUtil.setExecutableAttribute(gradlewFile.getPath(), true);
    }
  }

  /**
   * Given a project, return whether or not the project contains a module that uses the feature plugin. This method is used to
   * determine if a project is an old version of an instant app project.
   */
  public static boolean projectContainsFeatureModule(@NotNull Project project) {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(project);
    for(Module module : ModuleManager.getInstance(project).getModules()) {
      GradleBuildModel gradleBuildModel = projectBuildModel.getModuleBuildModel(module);
      if (gradleBuildModel != null) {
        List<String> plugins = PluginModel.extractNames(gradleBuildModel.plugins());
        if (plugins.contains("com.android.feature")) {
          return true;
        }
      }
    }
    return false;
  }
}

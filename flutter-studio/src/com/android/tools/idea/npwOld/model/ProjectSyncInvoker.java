/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npwOld.model;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ProjectSyncInvoker {
  /**
   * Triggers synchronizing the IDE model with the build system model of the project.
   */
  void syncProject(@NotNull Project project);

  /**
   * Triggers synchronizing using {@link ProjectSystemSyncManager}.
   */
  class DefaultProjectSyncInvoker implements ProjectSyncInvoker {
    @Override
    public void syncProject(@NotNull Project project) {
      //ProjectSystemUtil.getProjectSystem(project).getSyncManager().syncProject(PROJECT_MODIFIED);
    }
  }
}

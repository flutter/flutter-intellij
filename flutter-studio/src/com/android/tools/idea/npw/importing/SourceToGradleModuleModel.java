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
package com.android.tools.idea.npw.importing;

import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Model that represents the import of an existing library (Gradle project or Eclipse ADT project) into a Gradle project as a new Module.
 * Currently this Model actually delegates almost all of its work to the {@link WizardContext}. This is required as the steps that import an ADT
 * project are also used directly by IntelliJ in the "new project from existing source" flow, which uses a WizardContext for its state
 * (these steps are injected into the Wizard by the {@link SourceToGradleModuleStep}).
 */

public final class SourceToGradleModuleModel extends WizardModel {
  private final Project myProject;
  private final WizardContext myWizardContext;
  private final ProjectSyncInvoker myProjectSyncInvoker;
  private final Map<String, VirtualFile> myModulesToImport = Maps.newHashMap();

  private final StringProperty mySourceLocation = new StringValueProperty();

  public SourceToGradleModuleModel(@NotNull Project project, @NotNull ProjectSyncInvoker projectSyncInvoker) {
    myProject = project;
    myWizardContext = new WizardContext(project, this);
    myProjectSyncInvoker = projectSyncInvoker;
    mySourceLocation.addConstraint(String::trim);
  }

  @Override
  protected void handleFinished() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleImporter.getImporter(myWizardContext).importProjects(myModulesToImport);
      myProjectSyncInvoker.syncProject(myProject);
    });
  }

  @NotNull
  public StringProperty sourceLocation() {
    return mySourceLocation;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public WizardContext getContext() {
    return myWizardContext;
  }

  public void setModulesToImport(@NotNull Map<String, VirtualFile> value) {
    myModulesToImport.clear();
    myModulesToImport.putAll(value);
  }
}

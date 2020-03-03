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

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.npw.project.AndroidGradleModuleUtils.getContainingModule;

import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Model that represents the import of an existing library (.jar or .aar) into a Gradle project as a new Module
 */
public final class ArchiveToGradleModuleModel extends WizardModel {
  private final Project myProject;
  private final ProjectSyncInvoker myProjectSyncInvoker;

  private final StringProperty myArchive = new StringValueProperty();
  private final StringProperty myGradlePath = new StringValueProperty();
  private final BoolProperty myMoveArchive = new BoolValueProperty();

  public ArchiveToGradleModuleModel(@NotNull Project project, @NotNull ProjectSyncInvoker projectSyncInvoker) {
    myProject = project;
    myProjectSyncInvoker = projectSyncInvoker;
    myArchive.addConstraint(String::trim);
    myGradlePath.addConstraint(String::trim);
    //noinspection ConstantConditions
    myArchive.set(project.getBasePath());
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public StringProperty archive() {
    return myArchive;
  }

  @NotNull
  public StringProperty gradlePath() {
    return myGradlePath;
  }

  @NotNull
  public BoolProperty moveArchive() {
    return myMoveArchive;
  }

  @NotNull
  public BooleanExpression inModule() {
    return new BooleanExpression(myArchive) {
      @NotNull
      @Override
      public Boolean get() {
        return getContainingModule(new File(myArchive.get()), myProject) != null;
      }
    };
  }

  @Override
  protected void handleFinished() {
    String path = myGradlePath.get();

    new CreateModuleFromArchiveAction(
      myProject,
      path.startsWith(GRADLE_PATH_SEPARATOR) ? path : GRADLE_PATH_SEPARATOR + path,
      myArchive.get(),
      myMoveArchive.get(),
      getContainingModule(new File(myArchive.get()), myProject)).execute();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      assert ApplicationManager.getApplication().isDispatchThread();
      myProjectSyncInvoker.syncProject(myProject);
    }
  }

}

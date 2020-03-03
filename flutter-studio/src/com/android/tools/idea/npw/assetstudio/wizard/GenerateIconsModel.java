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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The model used to generate Android icons.
 * <p>
 * A wizard that owns this model is expected to call {@link #setIconGenerator(IconGenerator)}
 * at some point before finishing.
 */
public final class GenerateIconsModel extends WizardModel {
  @Nullable private IconGenerator myIconGenerator;
  @NotNull private AndroidModuleTemplate myPaths;
  @NotNull private final StateStorage myStateStorage;
  @NotNull private final String myWizardId;

  /**
   * Initializes the model.
   *
   * @param androidFacet the Android facet
   * @param wizardId the id of the wizard owning the model. Used as a key for storing wizard state.
   */
  public GenerateIconsModel(@NotNull AndroidFacet androidFacet, @NotNull String wizardId) {
    myPaths = AndroidPackageUtils.getModuleTemplates(androidFacet, null).get(0).getPaths();
    Project project = androidFacet.getModule().getProject();
    myStateStorage = ServiceManager.getService(project, StateStorage.class);
    assert myStateStorage != null;
    myWizardId = wizardId;
  }

  public void setIconGenerator(@NotNull IconGenerator iconGenerator) {
    myIconGenerator = iconGenerator;
  }

  @Nullable
  public IconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  public void setPaths(@NotNull AndroidModuleTemplate paths) {
    myPaths = paths;
  }

  @NotNull
  public AndroidModuleTemplate getPaths() {
    return myPaths;
  }

  @Override
  protected void handleFinished() {
    if (myIconGenerator == null) {
      getLog().error("GenerateIconsModel did not collect expected information and will not complete. Please report this error.");
      return;
    }

    myIconGenerator.generateIconsToDisk(myPaths);
  }

  /**
   * Returns the persistent state associated with the wizard.
   */
  @NotNull
  public PersistentState getPersistentState() {
    return myStateStorage.getState().getOrCreateChild(myWizardId);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(GenerateIconsModel.class);
  }

  @State(name = "WizardSettings", storages = @Storage("assetWizardSettings.xml"))
  public static class StateStorage implements PersistentStateComponent<PersistentState> {
    private PersistentState myState;

    @Override
    @NotNull
    public PersistentState getState() {
      if (myState == null) {
        myState = new PersistentState();
      }
      return myState;
    }

    @Override
    public void loadState(@NotNull PersistentState state) {
      myState = state;
    }

    @NotNull
    public static StateStorage getInstance(@NotNull Project project) {
      return ServiceManager.getService(project, StateStorage.class);
    }
  }
}

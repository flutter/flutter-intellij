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
package com.android.tools.idea.npwOld.assetstudio.wizard;

import com.android.tools.idea.npwOld.project.AndroidPackageUtils;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import java.util.Collection;
import java.util.Collections;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Step for generating Android icons from some image asset source.
 */
public final class NewImageAssetStep extends ModelWizardStep<GenerateIconsModel> {
  private static final String IMAGE_ASSET_PANEL_PROPERTY = "imageAssetPanel";

  @NotNull private final GenerateImageAssetPanel myGenerateImageAssetPanel;
  @NotNull private final AndroidFacet myFacet;

  public NewImageAssetStep(@NotNull GenerateIconsModel model, @NotNull AndroidFacet facet) {
    super(model, "Configure Image Asset");
    myGenerateImageAssetPanel = new GenerateImageAssetPanel(this, facet, model.getPaths());
    myFacet = facet;

    PersistentStateUtil.load(myGenerateImageAssetPanel, model.getPersistentState().getChild(IMAGE_ASSET_PANEL_PROPERTY));
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Collections.singletonList(new ConfirmGenerateImagesStep(getModel(), AndroidPackageUtils.getModuleTemplates(myFacet, null)));
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myGenerateImageAssetPanel;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myGenerateImageAssetPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(myGenerateImageAssetPanel.getIconGenerator());
  }

  @Override
  public void onWizardFinished() {
    getModel().getPersistentState().setChild(IMAGE_ASSET_PANEL_PROPERTY, myGenerateImageAssetPanel.getState());
  }
}

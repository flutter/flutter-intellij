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
package com.android.tools.idea.npw.template;

import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.wizard.GenerateImageAssetPanel;
import com.android.tools.idea.npw.model.RenderTemplateModel;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.templates.StringEvaluator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Strings;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Step for supporting a template.xml's {@code <icon>} tag if one exists (which tells the template
 * to also generate icons in addition to regular files).
 */
public final class GenerateIconsStep extends ModelWizardStep<RenderTemplateModel> {
  private final StudioWizardStepPanel myStudioPanel;
  private final GenerateImageAssetPanel myGenerateIconsPanel;

  private final ListenerManager myListeners = new ListenerManager();

  public GenerateIconsStep(AndroidFacet facet, @NotNull RenderTemplateModel model) {
    super(model, "Generate Icons");

    TemplateHandle templateHandle = getModel().getTemplateHandle();
    assert templateHandle != null;
    AndroidIconType iconType = templateHandle.getMetadata().getIconType();
    assert iconType != null; // It's an error to create <icon> tags w/o types.
    myGenerateIconsPanel = new GenerateImageAssetPanel(this, facet, model.getTemplate().get().getPaths(), iconType);

    myListeners.receiveAndFire(model.getTemplate(), value -> myGenerateIconsPanel.setProjectPaths(value.getPaths()));

    myStudioPanel = new StudioWizardStepPanel(myGenerateIconsPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  @Override
  protected void onEntering() {
    TemplateHandle templateHandle = getModel().getTemplateHandle();
    assert templateHandle != null;
    String iconNameExpression = templateHandle.getMetadata().getIconName();
    String iconName = null;
    if (iconNameExpression != null && !iconNameExpression.isEmpty()) {
      StringEvaluator evaluator = new StringEvaluator();
      iconName = evaluator.evaluate(iconNameExpression, getModel().getTemplateValues());
    }

    myGenerateIconsPanel.setOutputName(Strings.nullToEmpty(iconName));
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myGenerateIconsPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(myGenerateIconsPanel.getIconGenerator());
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }
}

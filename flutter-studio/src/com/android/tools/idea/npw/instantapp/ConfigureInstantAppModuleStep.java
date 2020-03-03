/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.instantapp;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.collect.Lists;
import java.util.Collection;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ConfigureInstantAppModuleStep extends SkippableWizardStep<NewInstantAppModuleModel> {
  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();

  private JPanel myPanel;
  private JTextField myModuleName;
  private JCheckBox myCreateIgnoreFile;

  public ConfigureInstantAppModuleStep(@NotNull NewInstantAppModuleModel model, String title) {
    super(model, title);

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    ModuleValidator moduleValidator = new ModuleValidator(model.getProject());
    myModuleName.setText(WizardUtils.getUniqueName(model.moduleName().get(), moduleValidator));
    TextProperty moduleNameText = new TextProperty(myModuleName);

    myBindings.bindTwoWay(new SelectedProperty(myCreateIgnoreFile), model.createGitIgnore());

    myValidatorPanel.registerValidator(moduleNameText, moduleValidator);

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Lists.newArrayList();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    // At this point, the validator panel should have no errors, and the user has typed a valid Module Name
    getModel().moduleName().set(myModuleName.getText());
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myModuleName;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}

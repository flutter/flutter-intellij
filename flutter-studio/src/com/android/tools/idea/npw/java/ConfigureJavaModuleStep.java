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
package com.android.tools.idea.npw.java;

import static com.android.tools.idea.npw.model.NewProjectModel.getInitialDomain;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.npw.validator.ClassNameValidator;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.Expression;
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


public class ConfigureJavaModuleStep extends SkippableWizardStep<NewJavaModuleModel> {
  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myPanel;
  private JTextField myLibraryName;
  private LabelWithEditButton myPackageName;
  private JTextField myClassName;
  private JCheckBox myCreateIgnoreFile;

  public ConfigureJavaModuleStep(@NotNull NewJavaModuleModel model, String title) {
    super(model, title);

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    ModuleValidator moduleValidator = new ModuleValidator(model.getProject());
    myLibraryName.setText(WizardUtils.getUniqueName(model.libraryNameName().get(), moduleValidator));
    TextProperty libraryNameText = new TextProperty(myLibraryName);
    myBindings.bind(model.libraryNameName(), libraryNameText, myValidatorPanel.hasErrors().not());
    myBindings.bindTwoWay(new TextProperty(myClassName), model.className());

    Expression<String> computedPackageName =
      new DomainToPackageExpression(new StringValueProperty(getInitialDomain(false)), model.libraryNameName());
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);

    TextProperty packageNameText = new TextProperty(myPackageName);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    myBindings.bindTwoWay(new SelectedProperty(myCreateIgnoreFile), model.createGitIgnore());

    myValidatorPanel.registerValidator(libraryNameText, moduleValidator);
    myValidatorPanel.registerValidator(model.packageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));
    myValidatorPanel.registerValidator(model.className(), new ClassNameValidator());

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

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myLibraryName;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}

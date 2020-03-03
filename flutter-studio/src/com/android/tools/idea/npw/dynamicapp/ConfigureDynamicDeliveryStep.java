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
package com.android.tools.idea.npw.dynamicapp;

import static com.android.tools.adtui.validation.Validator.Result.OK;
import static com.android.tools.adtui.validation.Validator.Severity.ERROR;
import static com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ui.components.JBLabel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigureDynamicDeliveryStep extends ModelWizardStep<DynamicFeatureModel> {
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myPanel;
  private JBLabel myFeatureTitleLabel;
  private JTextField myFeatureTitle;
  private JCheckBox myOnDemandCheckBox;
  private JCheckBox myFusingCheckBox;

  protected ConfigureDynamicDeliveryStep(@NotNull DynamicFeatureModel model) {
    super(model, message("android.wizard.module.new.dynamic.on.demand.options"));

    myValidatorPanel = new ValidatorPanel(this, wrappedWithVScroll(myPanel));
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myBindings.bindTwoWay(new TextProperty(myFeatureTitle), getModel().featureTitle());
    myBindings.bindTwoWay(new SelectedProperty(myOnDemandCheckBox), getModel().featureOnDemand());
    myBindings.bindTwoWay(new SelectedProperty(myFusingCheckBox), getModel().featureFusing());

    myListeners.receiveAndFire(getModel().featureOnDemand(), onDemandValue ->
      setEnabled(onDemandValue.booleanValue(), myFeatureTitleLabel, myFeatureTitle, myFusingCheckBox));

    myValidatorPanel.registerValidator(getModel().featureTitle(), value ->
      value.isEmpty() ? new Validator.Result(ERROR, message("android.wizard.validate.empty.name")) : OK);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFeatureTitle;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @Override
  protected boolean shouldShow() {
    return !getModel().instantModule().get();
  }

  private static void setEnabled(boolean isEnabled, JComponent... components) {
    for (JComponent component : components) {
      component.setEnabled(isEnabled);
    }
  }
}

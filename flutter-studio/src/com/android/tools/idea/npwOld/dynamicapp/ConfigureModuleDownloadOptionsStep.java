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
package com.android.tools.idea.npwOld.dynamicapp;

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
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.bool.IsEqualToExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import java.util.Optional;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigureModuleDownloadOptionsStep extends ModelWizardStep<DynamicFeatureModel> {
  @NotNull
  private final ValidatorPanel myValidatorPanel;
  @NotNull
  private final BindingsManager myBindings = new BindingsManager();
  @NotNull
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  @SuppressWarnings("unused") private JBLabel myFeatureTitleLabel;
  @SuppressWarnings("unused") private JBLabel myModuleDeliveryLabel;
  private JTextField myFeatureTitle;
  private JCheckBox myFusingCheckBox;
  private ModuleDownloadConditions myDownloadConditionsForm;
  private JComboBox<DownloadInstallKind> myInstallationOptionCombo;
  private HyperlinkLabel myHeaderLabel;
  private JBLabel myDeviceSupportHint;

  public ConfigureModuleDownloadOptionsStep(@NotNull DynamicFeatureModel model) {
    super(model, message("android.wizard.module.new.dynamic.download.options"));

    myHeaderLabel.setHyperlinkTarget("https://developer.android.com/reference/com/google/android/play/core/splitinstall/SplitInstallManager");
    myHeaderLabel.setHtmlText("<html>Dynamic modules can be included at install time or downloaded on-demand via the <a>SplitInstallManager API</a></html>");
    myInstallationOptionCombo.setModel(new DefaultComboBoxModel<>(DownloadInstallKind.values()));
    myDownloadConditionsForm.setModel(model.deviceFeatures());
    myValidatorPanel = new ValidatorPanel(this, wrappedWithVScroll(myRootPanel));
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
    myDeviceSupportHint.setForeground(JBColor.GRAY);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    super.onWizardStarting(wizard);

    myBindings.bindTwoWay(new TextProperty(myFeatureTitle), getModel().featureTitle());
    myBindings.bindTwoWay(new SelectedProperty(myFusingCheckBox), getModel().featureFusing());
    myBindings.bindTwoWay(new SelectedItemProperty<>(myInstallationOptionCombo), getModel().downloadInstallKind());

    // Initialize "conditions" sub-form
    BooleanExpression isConditionalPanelActive =
      new IsEqualToExpression<>(getModel().downloadInstallKind(), Optional.of(DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS));
    myDownloadConditionsForm
      .init(getModel().getProject(), myValidatorPanel, isConditionalPanelActive);

    // Show the "conditions" panel only if the dropdown selection is "with conditions"
    myListeners.listenAndFire(getModel().downloadInstallKind(), value ->
      setVisible(value.isPresent() && value.get() == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS,
                 myDownloadConditionsForm.myRootPanel));

    myValidatorPanel.registerValidator(getModel().featureTitle(), value ->
      StringUtil.isEmptyOrSpaces(value) ? new Validator.Result(ERROR, message("android.wizard.validate.empty.name")) : OK);
    }

  @Override
  protected void onEntering() {
    myFeatureTitle.selectAll();
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

  private static void setVisible(boolean isVisible, JComponent... components) {
    for (JComponent component : components) {
      component.setVisible(isVisible);
    }
  }
}

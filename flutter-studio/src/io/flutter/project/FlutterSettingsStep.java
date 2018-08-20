/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Configure Flutter project parameters that relate to platform-specific code.
 * These values are preserved (by the model) for use as defaults when other projects are created.
 */
public class FlutterSettingsStep extends SkippableWizardStep<FlutterProjectModel> {
  private final StudioWizardStepPanel myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myPanel;
  private JTextField myCompanyDomain;
  private LabelWithEditButton myPackageName;
  private JCheckBox myKotlinCheckBox;
  private JCheckBox mySwiftCheckBox;
  private JLabel myLanguageLabel;
  private boolean hasEntered = false;

  public FlutterSettingsStep(FlutterProjectModel model, String title, Icon icon) {
    super(model, title, icon);
    myKotlinCheckBox.setText(FlutterBundle.message("module.wizard.language.name_kotlin"));
    mySwiftCheckBox.setText(FlutterBundle.message("module.wizard.language.name_swift"));

    TextProperty packageNameText = new TextProperty(myPackageName);

    Expression<String> computedPackageName = new DomainToPackageExpression(model.companyDomain(), model.projectName()) {
      @Override
      public String get() {
        return super.get().replaceAll("_", "");
      }
    };
    BoolProperty isPackageSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.receive(packageNameText, value -> isPackageSynced.set(value.equals(computedPackageName.get())));

    myBindings.bindTwoWay(new TextProperty(myCompanyDomain), model.companyDomain());

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    myValidatorPanel.registerValidator(model.packageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myCompanyDomain;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onEntering() {
    if (hasEntered) {
      return;
    }
    // The language options are not used for modules. Since their values are persistent we cannot
    // establish bindings before knowing the project type.
    if (getModel().isModule()) {
      myLanguageLabel.setVisible(false);
      myKotlinCheckBox.setVisible(false);
      mySwiftCheckBox.setVisible(false);
    }
    else {
      myLanguageLabel.setVisible(true);
      myKotlinCheckBox.setVisible(true);
      mySwiftCheckBox.setVisible(true);
      myBindings.bindTwoWay(new SelectedProperty(myKotlinCheckBox), getModel().useKotlin());
      myBindings.bindTwoWay(new SelectedProperty(mySwiftCheckBox), getModel().useSwift());
    }
    hasEntered = true;
  }
}

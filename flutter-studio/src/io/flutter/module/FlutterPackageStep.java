/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.ObjectValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.adtui.util.FormScalingUtil;
import com.intellij.ide.projectWizard.ModuleNameLocationComponent;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.ReflectionUtil;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

public class FlutterPackageStep extends SkippableWizardStep<FlutterModuleModel> implements Disposable {

  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final ValidatorPanel myValidatorPanel;
  // TODO(messick): Update the root panel with whatever the latest and greatest is.
  @NotNull private final StudioWizardStepPanel myRootPanel;
  private FlutterModuleBuilder myBuilder;
  private FlutterModuleBuilder.FlutterModuleWizardStep oldStep;
  private JPanel myPanel;
  private ComboboxWithBrowseButton myFlutterSdkPath;
  private JPanel myModulePanel; // Used by form; do not delete until layout is fixed.
  private ModuleNameLocationComponent myModuleNameLocationComponent;

  public FlutterPackageStep(@NotNull FlutterModuleModel model, @NotNull String title, @NotNull Icon icon) {
    super(model, title, icon);
    model.setBuilder(myBuilder);
    myFlutterSdkPath.getComboBox().setEditable(true);
    FlutterSdkUtil.addKnownSDKPathsToCombo(myFlutterSdkPath.getComboBox());
    myFlutterSdkPath.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                             FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                             TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    myBindings.bind(model.flutterSdk(), new SelectedItemProperty<>(myFlutterSdkPath.getComboBox()));
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myValidatorPanel.registerValidator(model.flutterSdk(), value -> validateStep());
    myValidatorPanel.registerValidator(model.moduleName(), new ModuleValidator(model.getProject()));
    Expression<Boolean> isPanelValid = new Expression<Boolean>(new ObjectValueProperty<>(myModuleNameLocationComponent)) {
      @NotNull
      @Override
      public Boolean get() {
        // Only call this validate() when proceeding -- it creates the directory. Not good during initialization!
        // return myModuleNameLocationComponent.validate();
        try {
          invokePrivateMethod("validateExistingModuleName");
        }
        catch (ConfigurationException e) {
          return false;
        }
        return true;
      }
    };
    myValidatorPanel.registerTest(isPanelValid, "");
    Expression<Boolean> isStepValid = new Expression<Boolean>(new ObjectValueProperty<>(oldStep)) {
      @NotNull
      @Override
      public Boolean get() {
        try {
          return oldStep.validate();
        }
        catch (ConfigurationException e) {
          return false;
        }
      }
    };
    myValidatorPanel.registerTest(isStepValid, "");
    // TODO(messick): Fix validation.

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, FlutterBundle.message("module.wizard.package_step_body"));
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myModuleNameLocationComponent.getModuleNameField();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return new ArrayList<>();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private Validator.Result validateStep() {
    try {
      return oldStep.validate() ? Validator.Result.OK : Validator.Result.fromNullableMessage(oldStep.getValidationMessage());
    }
    catch (ConfigurationException e) {
      return Validator.Result.fromNullableMessage(e.getMessage());
    }
  }

  private void createUIComponents() {
    // This is called by the form builder before the constructor runs.
    WizardContext wizardContext = new WizardContext(getModel().getProject(), null);
    myBuilder = new FlutterModuleBuilder();
    wizardContext.setProjectBuilder(myBuilder);
    oldStep = (FlutterModuleBuilder.FlutterModuleWizardStep)myBuilder.getCustomOptionsStep(wizardContext, this);
    NamePathComponent namePathComponent = NamePathComponent.initNamePathComponent(wizardContext);
    namePathComponent.setShouldBeAbsolute(true);
    myModuleNameLocationComponent = new ModuleNameLocationComponent(wizardContext);
    myModuleNameLocationComponent.updateDataModel();
    myModuleNameLocationComponent.bindModuleSettings(namePathComponent);
    FlutterModuleModel model = getModel();
    model.setModuleComponent(myModuleNameLocationComponent);
  }

  private Object invokePrivateMethod(@NotNull String methodName) throws ConfigurationException {
    Method method =
      ReflectionUtil // Invoking a private method.
        .getDeclaredMethod(ModuleNameLocationComponent.class, methodName);
    try {
      assert method != null;
      return method.invoke(myModuleNameLocationComponent);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      return null;
    }
  }
}

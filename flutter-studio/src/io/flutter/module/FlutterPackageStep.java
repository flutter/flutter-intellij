/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.ide.projectWizard.ModuleNameLocationComponent;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class FlutterPackageStep extends SkippableWizardStep<FlutterModuleModel> implements Disposable {

  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final ValidatorPanel myValidatorPanel;

  @SuppressWarnings("deprecation") // TODO(messick): Update the root panel with whatever the latest and greatest is.
  @NotNull
  private final StudioWizardStepPanel myRootPanel;
  private final ModuleValidator myModuleValidator;
  private FlutterModuleBuilder myBuilder;
  private JPanel myPanel;
  private ComboboxWithBrowseButton myFlutterSdkPath;
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
    myModuleValidator = new ModuleValidator(model.getProject());
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myValidatorPanel.registerValidator(model.flutterSdk(), FlutterPackageStep::validateFlutterSdk);
    myValidatorPanel.registerValidator(model.moduleName(), this::validateFlutterModuleName);
    myValidatorPanel.registerValidator(model.moduleContentRoot(), FlutterPackageStep::validateFlutterModuleContentRoot);
    myValidatorPanel.registerValidator(model.moduleFileLocation(), FlutterPackageStep::validateFlutterModuleFileLocation);

    //noinspection deprecation
    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, FlutterBundle.message("module.wizard.package_step_body"));
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  private static Validator.Result validateFlutterSdk(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> sdkPath) {
    if (sdkPath.isPresent() && !sdkPath.get().isEmpty()) {
      final String message = FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(sdkPath.get());
      if (message != null) {
        return Validator.Result.fromNullableMessage(message);
      }
      return Validator.Result.OK;
    }
    else {
      return Validator.Result.fromNullableMessage("Flutter SDK path not given");
    }
  }

  @NotNull
  private static Validator.Result validateFlutterModuleContentRoot(String name) {
    return validatePath(name + String.valueOf(File.separatorChar), "Module content root", true);
  }

  @NotNull
  private static Validator.Result validateFlutterModuleFileLocation(String name) {
    return validatePath(name, "Module file location", false);
  }

  @NotNull
  private static Validator.Result validatePath(String path, String descr, boolean needsDirectory) {
    String name = FileUtil.toCanonicalPath(path, true);
    if (name == null || name.isEmpty()) {
      return Validator.Result.fromNullableMessage(descr + " not specified");
    }
    File file = new File(name);
    if (FileUtil.ensureCanCreateFile(file)) {
      if (!file.exists() || file.isDirectory() == needsDirectory) {
        return Validator.Result.OK;
      }
    }
    return Validator.Result.fromNullableMessage("Invalid path for " + descr.toLowerCase());
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

  @NotNull
  private Validator.Result validateFlutterModuleName(String name) {
    try {
      if (myBuilder.validateModuleName(myModuleNameLocationComponent.getModuleNameField().getText())) {
        return myModuleValidator.validate(name);
      }
      else {
        return Validator.Result.fromNullableMessage("Internal error"); // Not reached
      }
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
    // The custom options step must be created to initialize the builder.
    myBuilder.getCustomOptionsStep(wizardContext, this);
    NamePathComponent namePathComponent = NamePathComponent.initNamePathComponent(wizardContext);
    namePathComponent.setShouldBeAbsolute(true);
    myModuleNameLocationComponent = new ModuleNameLocationComponent(wizardContext);
    myModuleNameLocationComponent.updateDataModel();
    myModuleNameLocationComponent.bindModuleSettings(namePathComponent);
    FlutterModuleModel model = getModel();
    model.setModuleComponent(myModuleNameLocationComponent);
  }
}

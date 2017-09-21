/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.value.AsValueExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ComboboxWithBrowseButton;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Configure Flutter project parameters that are common to all types.
 */
public class FlutterProjectStep extends SkippableWizardStep<FlutterProjectModel> {
  private final StudioWizardStepPanel myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private boolean hasEntered = false;
  private OptionalValueProperty<FlutterProjectType> myProjectType;

  private JPanel myPanel;
  private JTextField myProjectName;
  private JTextField myDescription;
  private TextFieldWithBrowseButton myProjectLocation;
  private ComboboxWithBrowseButton myFlutterSdkPath;
  private JLabel myHeading;
  private JPanel myLocationPanel;

  public FlutterProjectStep(FlutterProjectModel model, String title, Icon icon, FlutterProjectType type) {
    super(model, title, icon);
    myProjectType = new OptionalValueProperty<>(type);

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    Expression<String> computedLocation = model.projectName().transform(FlutterProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getChildComponent());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(model.projectLocation(), locationText);
    myListeners.receive(locationText, value -> isLocationSynced.set(value.equals(computedLocation.get())));
    myBindings.bindTwoWay(new TextProperty(myProjectName), model.projectName());

    myBindings.bind(model.description(), new TextProperty(myDescription));

    myFlutterSdkPath.getComboBox().setEditable(true);
    myBindings.bind(model.flutterSdk(), new AsValueExpression<>(new SelectedItemProperty<>(myFlutterSdkPath.getComboBox())));
    FlutterSdkUtil.addKnownSDKPathsToCombo(myFlutterSdkPath.getComboBox());
    myFlutterSdkPath.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                             FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                             TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    myProjectLocation.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                              FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                              TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    myValidatorPanel.registerValidator(model.flutterSdk(), FlutterProjectStep::validateFlutterSdk);
    myValidatorPanel.registerValidator(model.projectName(), this::validateFlutterModuleName);

    Expression<File> locationFile = model.projectLocation().transform(File::new);
    myValidatorPanel.registerValidator(locationFile, PathValidator.createDefault("project location"));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  private static Validator.Result validateFlutterSdk(String sdkPath) {
    if (!sdkPath.isEmpty()) {
      final String message = FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(sdkPath);
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
  @SuppressWarnings("Duplicates") // Copied from ConfigureAndroidProjectStep
  private static String findProjectLocation(@NotNull String applicationName) {
    applicationName = NewProjectModel.sanitizeApplicationName(applicationName);
    File baseDirectory = WizardUtils.getProjectLocationParent();
    File projectDirectory = new File(baseDirectory, applicationName);

    // Try appName, appName2, appName3, ...
    int counter = 2;
    while (projectDirectory.exists()) {
      projectDirectory = new File(baseDirectory, String.format("%s%d", applicationName, counter++));
    }

    return projectDirectory.getPath();
  }

  private static Validator.Result errorResult(String message) {
    return new Validator.Result(Validator.Severity.ERROR, message);
  }

  /**
   * @See: https://www.dartlang.org/tools/pub/pubspec#name
   */
  @NotNull
  private Validator.Result validateFlutterModuleName(@NotNull String moduleName) {
    if (moduleName.isEmpty()) {
      return errorResult("Please enter a name for the project");
    }
    if (!FlutterUtils.isValidPackageName(moduleName)) {
      return errorResult(
        "Invalid module name: '" + moduleName + "' - must be a valid Dart package name (lower_case_with_underscores).");
    }
    if (FlutterUtils.isDartKeword(moduleName)) {
      return errorResult("Invalid module name: '" + moduleName + "' - must not be a Dart keyword.");
    }
    if (!FlutterUtils.isValidDartIdentifier(moduleName)) {
      return errorResult("Invalid module name: '" + moduleName + "' - must be a valid Dart identifier.");
    }
    if (FlutterConstants.FLUTTER_PACKAGE_DEPENDENCIES.contains(moduleName)) {
      return errorResult("Invalid module name: '" + moduleName + "' - this will conflict with Flutter package dependencies.");
    }
    if (moduleName.length() > FlutterConstants.MAX_MODULE_NAME_LENGTH) {
      return errorResult("Invalid module name - must be less than " +
                         FlutterConstants.MAX_MODULE_NAME_LENGTH +
                         " characters.");
    }
    if (getModel().project().get().isPresent()) {
      Project project = getModel().project().getValue();
      Module module;
      ProjectStructureConfigurable fromConfigurable = ProjectStructureConfigurable.getInstance(project);
      if (fromConfigurable != null) {
        module = fromConfigurable.getModulesConfig().getModule(moduleName);
      }
      else {
        module = ModuleManager.getInstance(project).findModuleByName(moduleName);
      }
      if (module != null) {
        return errorResult("A module with that name already exists in the project.");
      }
    }
    return Validator.Result.OK;
  }

  protected void hideLocation() {
    myLocationPanel.setVisible(false);
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
    return myProjectName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onEntering() {
    getModel().projectType().set(myProjectType);
    if (hasEntered) {
      return;
    }
    String heading = "";
    switch (myProjectType.getValue()) {
      case APP:
        heading = FlutterBundle.message("module.wizard.app_step_body");
        break;
      case PACKAGE:
        heading = FlutterBundle.message("module.wizard.package_step_body");
        break;
      case PLUGIN:
        heading = FlutterBundle.message("module.wizard.plugin_step_body");
        break;
    }
    myHeading.setText(heading);

    // Set default values for text fields, but only do so the first time the page is shown.
    String descrText = "";
    String name = "";
    switch (myProjectType.getValue()) {
      case APP:
        name = FlutterBundle.message("module.wizard.app_name");
        descrText = FlutterBundle.message("module.wizard.app_text");
        break;
      case PACKAGE:
        name = FlutterBundle.message("module.wizard.package_name");
        descrText = FlutterBundle.message("module.wizard.package_text");
        break;
      case PLUGIN:
        name = FlutterBundle.message("module.wizard.plugin_name");
        descrText = FlutterBundle.message("module.wizard.plugin_text");
        break;
    }
    myDescription.setText(descrText);
    myProjectName.setText(name);
    hasEntered = true;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    List<ModelWizardStep> allSteps = Lists.newArrayList();
    if (myProjectType.getValue() == FlutterProjectType.PACKAGE) {
      return allSteps;
    }
    allSteps.add(new FlutterSettingsStep(getModel(), getTitle(), getIcon()));
    return allSteps;
  }
}

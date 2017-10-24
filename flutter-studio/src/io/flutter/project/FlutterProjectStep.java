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
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.value.TransformOptionalExpression;
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
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.JBProgressBar;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.module.InstallSdkAction;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Configure Flutter project parameters that are common to all types.
 */
public class FlutterProjectStep extends SkippableWizardStep<FlutterProjectModel> implements InstallSdkAction.Model {
  private final StudioWizardStepPanel myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final InstallSdkAction myInstallSdkAction;
  private boolean hasEntered = false;
  private OptionalValueProperty<FlutterProjectType> myProjectType;
  private StringValueProperty myDownloadErrorMessage = new StringValueProperty();
  private InstallSdkAction.CancelActionListener myListener;
  private String mySdkPathContent = "";

  private JPanel myPanel;
  private JTextField myProjectName;
  private JTextField myDescription;
  private TextFieldWithBrowseButton myProjectLocation;
  private ComboboxWithBrowseButton myFlutterSdkPath;
  private JLabel myHeading;
  private JPanel myLocationPanel;
  private LinkLabel myInstallActionLink;
  private JBProgressBar myProgressBar;
  private JLabel myCancelProgressButton;
  private JTextPane myProgressText;

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
    myFlutterSdkPath.getButton()
      .addActionListener((e) -> myFlutterSdkPath.getComboBox().setSelectedItem(myFlutterSdkPath.getComboBox().getEditor().getItem()));
    myBindings.bind(
      model.flutterSdk(),
      new TransformOptionalExpression<String, String>("", new SelectedItemProperty<>(myFlutterSdkPath.getComboBox())) {
        @NotNull
        @Override
        protected String transform(@NotNull String value) {
          return value;
        }
      });
    FlutterSdkUtil.addKnownSDKPathsToCombo(myFlutterSdkPath.getComboBox());
    myFlutterSdkPath.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                             FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                             TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    myProjectLocation.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                              FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                              TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    myValidatorPanel.registerValidator(model.flutterSdk(), FlutterProjectStep::validateFlutterSdk);
    myValidatorPanel.registerValidator(model.projectName(), this::validateFlutterModuleName);
    myValidatorPanel.registerMessageSource(myDownloadErrorMessage);

    Expression<File> locationFile = model.projectLocation().transform(File::new);
    myValidatorPanel.registerValidator(locationFile, PathValidator.createDefault("project location"));

    // Initialization of the SDK install UI was copied from FlutterGeneratorPeer.

    // Hide pending real content.
    myProgressBar.setVisible(false);
    myProgressText.setVisible(false);
    myCancelProgressButton.setVisible(false);
    myInstallSdkAction = new InstallSdkAction(this);

    myInstallActionLink.setIcon(myInstallSdkAction.getLinkIcon());
    myInstallActionLink.setDisabledIcon(IconLoader.getDisabledIcon(myInstallSdkAction.getLinkIcon()));

    myInstallActionLink.setText(myInstallSdkAction.getLinkText());

    //noinspection unchecked
    myInstallActionLink.setListener((label, linkUrl) -> myInstallSdkAction.actionPerformed(null), null);

    myProgressText.setFont(UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.ITALIC));

    // Some feedback on hover.
    myCancelProgressButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myCancelProgressButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myListener.actionCanceled();
      }
    });

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
  private static String findProjectLocation(@NotNull String appName) {
    String applicationName = NewProjectModel.sanitizeApplicationName(appName);
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

  private static void ensureComboModelContainsCurrentItem(@NotNull final JComboBox comboBox) {
    // TODO(messick): Make the original version of this public in the Dart plugin. Cache comboBox.getModel().
    final Object currentItem = comboBox.getEditor().getItem();

    boolean contains = false;
    for (int i = 0; i < comboBox.getModel().getSize(); i++) {
      if (currentItem.equals(comboBox.getModel().getElementAt(i))) {
        contains = true;
        break;
      }
    }

    if (!contains) {
      //noinspection unchecked
      ((DefaultComboBoxModel)comboBox.getModel()).insertElementAt(currentItem, 0);
      comboBox.setSelectedItem(currentItem); // to set focus on current item in combo popup
      comboBox.getEditor().setItem(currentItem); // to set current item in combo itself
    }
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
    if (FlutterUtils.isDartKeyword(moduleName)) {
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
    myDownloadErrorMessage.set("");
    mySdkPathContent = (String)myFlutterSdkPath.getComboBox().getEditor().getItem();

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

  @Override
  public void setSdkPath(String path) {
    mySdkPathContent = (String)myFlutterSdkPath.getComboBox().getEditor().getItem();
    myFlutterSdkPath.getComboBox().getEditor().setItem(path);
    myDownloadErrorMessage.set("");
  }

  @Override
  public boolean validate() {
    setSdkPath(mySdkPathContent);
    ensureComboModelContainsCurrentItem(myFlutterSdkPath.getComboBox());
    return false;
  }

  @Override
  public void requestNextStep() {
    // Called when the download process completes.
    ensureComboModelContainsCurrentItem(myFlutterSdkPath.getComboBox());
  }

  @Override
  public void setErrorDetails(String details) {
    // Setting this doesn't do much since the first validation error encountered is displayed, not the most recent.
    myDownloadErrorMessage.set(details == null ? "" : details);
  }

  @Override
  public ComboboxWithBrowseButton getSdkComboBox() {
    return myFlutterSdkPath;
  }

  @Override
  public void addCancelActionListener(InstallSdkAction.CancelActionListener listener) {
    myListener = listener;
  }

  @Override
  public JBProgressBar getProgressBar() {
    return myProgressBar;
  }

  @Override
  public LinkLabel getInstallActionLink() {
    return myInstallActionLink;
  }

  @Override
  public JTextPane getProgressText() {
    return myProgressText;
  }

  @Override
  public JLabel getCancelProgressButton() {
    return myCancelProgressButton;
  }
}

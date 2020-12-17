/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.value.TransformOptionalExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.module.settings.FlutterCreateParams;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import java.awt.Color;
import java.io.File;
import java.util.Collection;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configure Flutter project parameters that are common to all types.
 */
public class FlutterProjectStep extends SkippableWizardStep<FlutterProjectModel> {
  private final JBScrollPane myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private boolean hasEntered = false;
  private OptionalValueProperty<FlutterProjectType> myProjectType;
  private StringValueProperty myDownloadErrorMessage = new StringValueProperty();

  private JPanel myPanel;
  private JTextField myProjectName;
  private JTextField myDescription;
  private TextFieldWithBrowseButton myProjectLocation;
  private ComboboxWithBrowseButton myFlutterSdkPath;
  private JLabel myHeading;
  private JPanel myLocationPanel;
  private FlutterCreateParams myCreateParams;
  private Color sdkBackgroundColor;

  public FlutterProjectStep(FlutterProjectModel model, String title, Icon icon, FlutterProjectType type) {
    super(model, title, icon);
    myProjectType = new OptionalValueProperty<>(type);
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = wrappedWithVScroll(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    FlutterProjectModel model = getModel();
    String initialLocation = WizardUtils.getProjectLocationParent().getPath();
    // Directionality matters. Let the bindings set the model's value from the text field.
    myProjectLocation.getChildComponent().setText(initialLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getChildComponent());
    myBindings.bind(model.projectLocation(), locationText);
    myProjectName.setText(model.projectName().get());
    myBindings.bind(getModel().projectName(), new TextProperty(myProjectName));

    myBindings.bind(model.description(), new TextProperty(myDescription));

    myFlutterSdkPath.getComboBox().setEditable(true);
    myFlutterSdkPath.getButton().addActionListener(
      (e) -> myFlutterSdkPath.getComboBox().setSelectedItem(myFlutterSdkPath.getComboBox().getEditor().getItem()));
    myBindings.bind(
      model.flutterSdk(),
      new TransformOptionalExpression<String, String>("", new SelectedItemProperty<>(myFlutterSdkPath.getComboBox())) {
        @NotNull
        @Override
        protected String transform(@NotNull String value) {
          return value;
        }
      });
    myBindings.bindTwoWay(model.isOfflineSelected(), new SelectedProperty(myCreateParams.getOfflineCheckbox()));
    myCreateParams.setInitialValues();

    FlutterSdkUtil.addKnownSDKPathsToCombo(myFlutterSdkPath.getComboBox());
    myFlutterSdkPath.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                             FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                             TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    myProjectLocation.addBrowseFolderListener(FlutterBundle.message("flutter.module.create.settings.location.select"), null, null,
                                              FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                              TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    myValidatorPanel.registerValidator(model.flutterSdk(), FlutterProjectStep::validateFlutterSdk);
    myValidatorPanel.registerValidator(model.projectName(), this::validateFlutterModuleName);
    myValidatorPanel.registerMessageSource(myDownloadErrorMessage);

    if (isProject()) {
      Expression<File> locationFile = model.projectLocation().transform(File::new);
      myValidatorPanel.registerValidator(locationFile, new PathValidator.Builder().withCommonRules().build("project location"));
      final JTextComponent sdkEditor = (JTextComponent)myFlutterSdkPath.getComboBox().getEditor().getEditorComponent();
      sdkBackgroundColor = sdkEditor.getBackground();
      sdkEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          updateSdkField(sdkEditor);
        }
      });
    }
    else {
      myCreateParams.getOfflineCheckbox().setText("Create module offline");
    }
  }

  private void updateSdkField(JTextComponent sdkEditor) {
    FlutterSdk current = FlutterSdk.forPath(sdkEditor.getText());
    Color color = sdkBackgroundColor;
    if (current == null) {
      if (ColorUtil.isDark(sdkBackgroundColor)) {
        color = ColorUtil.darker(JBColor.YELLOW, 5);
      }
      else {
        color = ColorUtil.desaturate(JBColor.YELLOW, 15);
      }
    }
    sdkEditor.setBackground(color);
  }

  /**
   * @see <a href="dart.dev/tools/pub/pubspec#name">https://dart.dev/tools/pub/pubspec#name</a>
   */
  @NotNull
  private Validator.Result validateFlutterModuleName(@NotNull String moduleName) {
    if (moduleName.isEmpty()) {
      return errorResult("Please enter a name for the " + getContainerName() + ".");
    }
    File loc = new File(FileUtil.toSystemDependentName(getModel().projectLocation().get()), getModel().projectName().get());
    if (loc.exists()) {
      return errorResult("Project location already exists: " + loc.getPath());
    }
    if (!FlutterUtils.isValidPackageName(moduleName)) {
      return errorResult(
        "Invalid " + getContainerName() + " name: '" + moduleName + "' - must be a valid Dart package name (lower_case_with_underscores).");
    }
    if (FlutterUtils.isDartKeyword(moduleName)) {
      return errorResult("Invalid module name: '" + moduleName + "' - must not be a Dart keyword.");
    }
    // Package name is more restrictive than identifier, so no need to check for identifier validity.
    if (FlutterConstants.FLUTTER_PACKAGE_DEPENDENCIES.contains(moduleName)) {
      return errorResult(
        "Invalid " + getContainerName() + " name: '" + moduleName + "' - this will conflict with Flutter package dependencies.");
    }
    if (moduleName.length() > FlutterConstants.MAX_MODULE_NAME_LENGTH) {
      return errorResult("Invalid " + getContainerName() + " name - must be less than " +
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

  @NotNull
  public String getContainerName() {
    return "project";
  }

  protected boolean isProject() {
    return true;
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
    myFlutterSdkPath.getComboBox().getEditor().setItem(getModel().flutterSdk().get());
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
      case MODULE:
        heading = FlutterBundle.message("module.wizard.module_step_body");
        break;
      case IMPORT:
        throw new Error("Import is handled in a different class");
    }
    myHeading.setText(heading);
    myDownloadErrorMessage.set("");

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
      case MODULE:
        name = FlutterBundle.message("module.wizard.module_name");
        descrText = FlutterBundle.message("module.wizard.module_text");
        break;
      case IMPORT:
        throw new Error("Import is handled in a different class");
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
      return Validator.Result.fromNullableMessage("Flutter SDK path not given.");
    }
  }

  private static Validator.Result errorResult(String message) {
    return new Validator.Result(Validator.Severity.ERROR, message);
  }

  public static void ensureComboModelContainsCurrentItem(@NotNull final JComboBox comboBox) {
    // TODO(messick): Replace the original in the Dart plugin with this implementation.
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
    }
    comboBox.setSelectedItem(currentItem); // to set focus on current item in combo popup
    comboBox.getEditor().setItem(currentItem); // to set current item in combo itself
  }

  /**
   * When creating a StudioWizardStepPanel which may be so tall as to require vertical scrolling,
   * using this helper method to automatically wrap it with an appropriate JScrollPane.
   */
  @NotNull
  public static JBScrollPane wrappedWithVScroll(@NotNull JPanel innerPanel) {
    JBScrollPane sp = new JBScrollPane(new StudioWizardStepPanel(innerPanel), VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    sp.setBorder(BorderFactory.createEmptyBorder());
    return sp;
  }
}

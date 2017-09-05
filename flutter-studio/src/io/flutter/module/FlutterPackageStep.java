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
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.labels.LinkLabel;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

@SuppressWarnings("Duplicates")
public class FlutterPackageStep extends SkippableWizardStep<FlutterModuleModel> implements Disposable {

  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final ValidatorPanel myValidatorPanel;

  private final WizardContext myWizardContext;

  @SuppressWarnings("deprecation") // TODO(messick): Update the root panel with whatever the latest and greatest is.
  @NotNull
  private final StudioWizardStepPanel myRootPanel;
  private final ModuleValidator myModuleValidator;
  private FlutterModuleBuilder myBuilder;
  private JPanel myPanel;
  private ComboboxWithBrowseButton myFlutterSdkPath;
  private JPanel myModulePanel;
  private JTextField myModuleName;
  private AdditionalLanguageSettings myAdditionalSettings;
  private JTextField myDescription;
  private LinkLabel<String> myFlutterDocsUrl;
  private JLabel myProjectNameDescription;
  private JLabel myOrgDescription;
  private JLabel myOrgLabel;
  private JLabel myProjectNameLabel;
  private JLabel myModuleDescription;
  private JLabel mySdkPathLabel;
  private String myModuleContentPath = "";

  public FlutterPackageStep(@NotNull FlutterModuleModel model,
                            @NotNull String title,
                            @NotNull Icon icon,
                            @NotNull FlutterProjectType projectType) {
    super(model, title, icon);
    boolean isPlugin = projectType == FlutterProjectType.PLUGIN;
    myWizardContext = new WizardContext(getModel().getProject(), null);
    myBuilder = new FlutterModuleBuilder();
    final FlutterCreateAdditionalSettings settings = myBuilder.getAdditionalSettings();
    myWizardContext.setProjectBuilder(myBuilder);
    NamePathComponent namePathComponent = NamePathComponent.initNamePathComponent(myWizardContext);
    namePathComponent.setShouldBeAbsolute(true);
    updateDataModel();
    bindModuleSettings(namePathComponent);
    model.setModuleComponent(this);
    if (isPlugin) {
      mySdkPathLabel.setPreferredSize(myAdditionalSettings.getLabelColumnSize());
      myAdditionalSettings.setInitialValues(settings);
    }
    else {
      myAdditionalSettings.getComponent().setVisible(false);
      myOrgLabel.setVisible(false);
      myOrgDescription.setVisible(false);
    }

    model.setBuilder(myBuilder);
    myBuilder.getCustomOptionsStep(myWizardContext, this); // TODO 'this' may be the wrong disposer; getting a memory leak somewhere.
    settings.setType(projectType);

    myDescription.setText(settings.getDescription());
    myBindings.bind(new StringProperty() {
      @NotNull
      @Override
      public String get() {
        return settings.getDescription() == null ? "" : settings.getDescription();
      }

      @Override
      protected void setDirectly(@NotNull String value) {
        settings.setDescription(value);
      }
    }, new TextProperty(myDescription));

    if (isPlugin) {
      myBindings.bind(new StringProperty() {
        @NotNull
        @Override
        public String get() {
          return settings.getOrg() == null ? "" : settings.getOrg();
        }

        @Override
        protected void setDirectly(@NotNull String value) {
          settings.setOrg(value);
        }
      }, new TextProperty(myAdditionalSettings.getOrganizationField()));
      myBindings.bind(new BoolProperty() {
        @NotNull
        @Override
        public Boolean get() {
          return settings.getKotlin() == null ? false : settings.getKotlin();
        }

        @Override
        protected void setDirectly(@NotNull Boolean value) {
          settings.setKotlin(value);
        }
      }, new SelectedProperty(myAdditionalSettings.getKotlinRadioButton()));
      myBindings.bind(new BoolProperty() {
        @NotNull
        @Override
        public Boolean get() {
          return settings.getSwift() == null ? false : settings.getSwift();
        }

        @Override
        protected void setDirectly(@NotNull Boolean value) {
          settings.setSwift(value);
        }
      }, new SelectedProperty(myAdditionalSettings.getSwiftRadioButton()));
    }

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

    myFlutterDocsUrl.setText(FlutterBundle.message("flutter.module.create.settings.help.packages_and_plugins_text"));
    myFlutterDocsUrl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myFlutterDocsUrl.setIcon(null);
    myFlutterDocsUrl
      .setListener((label, linkUrl) -> BrowserLauncher.getInstance().browse("https://flutter.io/developing-packages/", null), null);

    myProjectNameLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.module_name.label"));
    myProjectNameDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.module_name.description"));
    if (isPlugin) {
      myModuleDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.type.plugin"));
      myOrgLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.org.label"));
      myOrgDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.org.description"));
    } else {
      myModuleDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.type.package"));
    }

    String header = isPlugin
                    ? FlutterBundle.message("module.wizard.plugin_step_body")
                    : FlutterBundle.message("module.wizard.package_step_body");
    //noinspection deprecation
    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, header);
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

  private static String getDefaultBaseDir(WizardContext wizardContext, NamePathComponent namePathComponent) {
    if (wizardContext.isCreatingNewProject()) {
      return namePathComponent.getPath();
    }
    else {
      final Project project = wizardContext.getProject();
      assert project != null;
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPath();
      }
      return "";
    }
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return getModuleNameField();
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
      if (myBuilder.validateModuleName(getModuleNameField().getText())) {
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

  @Nullable
  private AbstractModuleBuilder getModuleBuilder() {
    return ((AbstractModuleBuilder)myWizardContext.getProjectBuilder());
  }

  /**
   * @see ModuleWizardStep#validate()
   */
  public boolean validate() throws ConfigurationException {
    AbstractModuleBuilder builder = getModuleBuilder();
    if (builder != null && !builder.validateModuleName(getModuleName())) return false;
    if (!validateModulePaths()) return false;
    validateExistingModuleName();
    return true;
  }

  /**
   * @see ModuleWizardStep#updateDataModel()
   */
  public void updateDataModel() {
    AbstractModuleBuilder moduleBuilder = getModuleBuilder();
    if (moduleBuilder == null) return;

    final String moduleName = getModuleName();
    moduleBuilder.setName(moduleName);
    moduleBuilder.setModuleFilePath(
      FileUtil.toSystemIndependentName(getModuleContentPath()) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    moduleBuilder.setContentEntryPath(FileUtil.toSystemIndependentName(getModuleContentPath()));
  }

  public JPanel getModulePanel() {
    return myModulePanel;
  }

  private void bindModuleSettings(final NamePathComponent namePathComponent) {
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(final DocumentEvent e) {
        String path = getDefaultBaseDir(myWizardContext, namePathComponent);
        final String moduleName = getModuleName();
        if (!path.isEmpty() && !Comparing.strEqual(moduleName, namePathComponent.getNameValue())) {
          path += "/" + moduleName;
        }
          setModuleContentRoot(path);
      }
    });
    if (myWizardContext.isCreatingNewProject()) {
      setModuleName(namePathComponent.getNameValue());
      setModuleContentRoot(namePathComponent.getPath());
    }
    else {
      final Project project = myWizardContext.getProject();
      assert project != null;
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) { //e.g. was deleted
        final String baseDirPath = baseDir.getPath();
        String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, "untitled", "");
        String contentRoot = baseDirPath + "/" + moduleName;
        if (!Comparing.strEqual(project.getName(), myWizardContext.getProjectName()) &&
            !myWizardContext.isCreatingNewProject() &&
            myWizardContext.getProjectName() != null) {
          moduleName =
            ProjectWizardUtil.findNonExistingFileName(myWizardContext.getProjectFileDirectory(), myWizardContext.getProjectName(), "");
          contentRoot = myWizardContext.getProjectFileDirectory();
        }
        setModuleName(moduleName);
        setModuleContentRoot(contentRoot);
        myModuleName.select(0, moduleName.length());
      }
    }
  }

  private void validateExistingModuleName() throws ConfigurationException {
    Project project = myWizardContext.getProject();
    if (project == null) return;

    final String moduleName = getModuleName();
    final Module module;
    final ProjectStructureConfigurable fromConfigurable = ProjectStructureConfigurable.getInstance(project);
    if (fromConfigurable != null) {
      module = fromConfigurable.getModulesConfig().getModule(moduleName);
    }
    else {
      module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    }
    if (module != null) {
      throw new ConfigurationException("Module \'" + moduleName + "\' already exist in project. Please, specify another name.");
    }
  }

  private boolean validateModulePaths() throws ConfigurationException {
    final String moduleName = getModuleName();
    final String moduleFileDirectory = getModuleContentPath();
    if (moduleFileDirectory.isEmpty()) {
      throw new ConfigurationException("Enter module file location");
    }
    if (moduleName.isEmpty()) {
      throw new ConfigurationException("Enter a module name");
    }

    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.file"), moduleFileDirectory,
                                                      false)) {
      return false;
    }

    File moduleFile = new File(moduleFileDirectory, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    if (moduleFile.exists()) {
      int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", moduleFile.getAbsolutePath(),
                                                              IdeBundle.message("project.new.wizard.module.identification")),
                                            IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      if (answer != Messages.YES) {
        return false;
      }
    }
    return true;
  }

  public String getModuleContentPath() {
    return myModuleContentPath;
  }

  private void setModuleContentRoot(final String path) {
    myModuleContentPath = path;
  }

  public JTextField getModuleNameField() {
    return myModuleName;
  }

  private String getModuleName() {
    return myModuleName.getText().trim();
  }

  public void setModuleName(String moduleName) {
    myModuleName.setText(moduleName);
  }
}

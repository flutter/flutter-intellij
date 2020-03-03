/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.importing;

import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.DOES_NOT_EXIST;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.EMPTY_PATH;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.INTERNAL_ERROR;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.IS_PROJECT_OR_MODULE;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.MISSING_SUBPROJECTS;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.NOT_ADT_OR_GRADLE;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.NO_MODULES_SELECTED;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.OK;
import static com.android.tools.idea.npw.importing.SourceToGradleModuleStep.PathValidationResult.ResultType.VALIDATING;
import static com.intellij.openapi.ui.MessageType.ERROR;
import static com.intellij.openapi.ui.MessageType.WARNING;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.gradle.project.ModuleToImport;
import com.android.tools.idea.npw.AsyncValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.IconProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard.Facade;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard Step that allows the user to point to an existing source directory (ADT or Gradle) to import as a new Android Gradle module.
 * Also allows selection of sub-modules to import. Most functionality is contained within existing {@link ModulesTable} class.
 */
public final class SourceToGradleModuleStep extends SkippableWizardStep<SourceToGradleModuleModel> {
  private final ListenerManager myListeners = new ListenerManager();
  private final BindingsManager myBindings = new BindingsManager();

  private final BoolProperty myCanGoForward = new BoolValueProperty();
  private final ObjectProperty<PathValidationResult> myPageValidationResult = new ObjectValueProperty<>(PathValidationResult.ofType(OK));

  @NotNull private PathValidationResult myLocationValidationResult = PathValidationResult.ofType(OK);
  // Facade is initialised dynamically
  @Nullable private Facade myFacade;

  private JPanel myPanel;
  private TextFieldWithBrowseButton mySourceLocation;
  private JBLabel myErrorWarning;
  private AsyncProcessIcon myValidationProgress;
  private JBScrollPane myModulesScroller;
  private ModulesTable myModulesPanel;
  private JLabel myRequiredModulesLabel;
  private JLabel myModuleNameLabel;
  private JTextField myModuleNameField;
  private JLabel myPrimaryModuleState;

  public SourceToGradleModuleStep(@NotNull SourceToGradleModuleModel model) {
    super(model, AndroidBundle.message("android.wizard.module.import.source.title"));

    //noinspection DialogTitleCapitalization - incorrectly detects "Gradle" as incorrectly capitalised
    mySourceLocation.addBrowseFolderListener(AndroidBundle.message("android.wizard.module.import.source.browse.title"),
                                             AndroidBundle.message("android.wizard.module.import.source.browse.description"),
                                             getModel().getProject(),
                                             FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor());

    myBindings.bindTwoWay(new TextProperty(mySourceLocation.getTextField()), model.sourceLocation());

    myBindings.bind(new VisibleProperty(myValidationProgress), myPageValidationResult.transform(PathValidationResult::isValidating));

    myBindings.bind(new VisibleProperty(myErrorWarning), myPageValidationResult.transform(result -> result.getIcon() != null));
    myBindings.bind(new TextProperty(myErrorWarning), myPageValidationResult.transform(PathValidationResult::getMessage));
    myBindings.bind(new IconProperty(myErrorWarning), myPageValidationResult.transform(result -> Optional.ofNullable(result.getIcon())));

    myErrorWarning.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
    myPanel.setBorder(new EmptyBorder(UIUtil.PANEL_REGULAR_INSETS));

    myModulesPanel.bindPrimaryModuleEntryComponents(new PrimaryModuleImportSettings(), myRequiredModulesLabel);
    myModulesPanel.addPropertyChangeListener(ModulesTable.PROPERTY_SELECTED_MODULES, event -> {
      if (ModulesTable.PROPERTY_SELECTED_MODULES.equals(event.getPropertyName())) {
        updateStepStatus();
      }
    });

    AsyncValidator<?> validator = new AsyncValidator<PathValidationResult>(ApplicationManager.getApplication()) {
      @Override
      protected void showValidationResult(@NotNull PathValidationResult result) {
        applyValidationResult(result);
      }

      @NotNull
      @Override
      protected PathValidationResult validate() {
        myPageValidationResult.set(PathValidationResult.ofType(VALIDATING));
        return checkPath(getModel().sourceLocation().get());
      }
    };

    myListeners.listen(model.sourceLocation(), source -> validator.invalidate());
  }

  @Override
  protected void onWizardStarting(@NotNull Facade wizard) {
    myFacade = wizard;
  }

  @Override
  protected void onProceeding() {
    getModel().setModulesToImport(myModulesPanel.getSelectedModulesMap());
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myCanGoForward;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return mySourceLocation;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    WizardContext context = getModel().getContext();
    ArrayList<ModelWizardStep> wrappedSteps = new ArrayList<>();

    for (ModuleImporter importer : ModuleImporter.getAllImporters(context)) {
      for (ModuleWizardStep inputStep : importer.createWizardSteps()) {
        wrappedSteps.add(new ModuleWizardStepAdapter(context, inputStep));
      }
    }

    return wrappedSteps;
  }

  private void applyValidationResult(@NotNull PathValidationResult result) {
    myLocationValidationResult = result;

    myModulesPanel.setModules(getModel().getProject(), result.myVFile, result.myModules);
    myModulesScroller.setVisible(myModulesPanel.getComponentCount() > 0);

    // Setting the active importer affects the visibility of other steps in the wizard so we need to call updateNavigationProperties
    // to make sure Finish / Next is displayed correctly
    ModuleImporter.setImporter(getModel().getContext(), result.myImporter);
    assert myFacade != null;
    myFacade.updateNavigationProperties();

    updateStepStatus();
  }

  private void updateStepStatus() {
    PathValidationResult result = myLocationValidationResult;

    // Validation of import location can be superseded by lack of modules selected for import
    if (result.myStatus.severity != ERROR && myModulesPanel.getSelectedModules().isEmpty()) {
      result = PathValidationResult.ofType(NO_MODULES_SELECTED);
    }

    myPageValidationResult.set(result);
    myCanGoForward.set(result.myStatus.severity != ERROR && !result.isValidating() && myModulesPanel.canImport());
  }

  private void createUIComponents() {
    myValidationProgress = new AsyncProcessIcon("validation");
  }

  @NotNull
  @VisibleForTesting
  PathValidationResult checkPath(@NotNull String path) {
    if (Strings.isNullOrEmpty(path)) {
      return PathValidationResult.ofType(EMPTY_PATH);
    }
    VirtualFile vFile = VfsUtil.findFileByIoFile(new File(path), false);
    if (vFile == null || !vFile.exists()) {
      return PathValidationResult.ofType(DOES_NOT_EXIST);
    }
    else if (isProjectOrModule(vFile)) {
      return PathValidationResult.ofType(IS_PROJECT_OR_MODULE);
    }
    ModuleImporter importer = ModuleImporter.importerForLocation(getModel().getContext(), vFile);
    if (!importer.isValid()) {
      return PathValidationResult.ofType(NOT_ADT_OR_GRADLE);
    }
    Collection<ModuleToImport> modules = ApplicationManager.getApplication().runReadAction((Computable<Collection<ModuleToImport>>)() -> {
      try {
        return importer.findModules(vFile);
      }
      catch (IOException e) {
        Logger.getInstance(SourceToGradleModuleStep.class).error(e);
        return null;
      }
    });
    if (modules == null) {
      return PathValidationResult.ofType(INTERNAL_ERROR);
    }
    Set<String> missingSourceModuleNames = Sets.newTreeSet();
    for (ModuleToImport module : modules) {
      if (module.location == null || !module.location.exists()) {
        missingSourceModuleNames.add(module.name);
      }
    }
    if (!missingSourceModuleNames.isEmpty()) {
      return new PathValidationResult(MISSING_SUBPROJECTS, vFile, importer, modules, missingSourceModuleNames);
    }
    return new PathValidationResult(OK, vFile, importer, modules, null);
  }

  private boolean isProjectOrModule(@NotNull VirtualFile dir) {
    Project project = getModel().getProject();
    if (dir.equals(project.getBaseDir())) {
      return true;
    }

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (ModuleUtilCore.isModuleDir(module, dir)) {
        return true;
      }
    }

    return false;
  }

  static final class PathValidationResult {
    @NotNull public final ResultType myStatus;
    @Nullable public final VirtualFile myVFile;
    @Nullable public final ModuleImporter myImporter;
    @Nullable public final Collection<ModuleToImport> myModules;
    @Nullable public final Set<String> myDetails;

    private PathValidationResult(@NotNull ResultType status,
                                 @Nullable VirtualFile vFile,
                                 @Nullable ModuleImporter importer,
                                 @Nullable Collection<ModuleToImport> modules,
                                 @Nullable Set<String> details) {
      myStatus = status;
      myVFile = vFile;
      myImporter = importer;
      myModules = modules;
      myDetails = details;
    }

    @Nullable
    public Icon getIcon() {
      return myStatus.getIcon();
    }

    public String getMessage() {
      return myStatus.getMessage(myDetails);
    }

    public boolean isValidating() {
      return myStatus == VALIDATING;
    }

    public static PathValidationResult ofType(ResultType status) {
      return new PathValidationResult(status, null, null, null, null);
    }

    enum ResultType {
      OK(null, null),
      EMPTY_PATH(AndroidBundle.message("android.wizard.module.import.source.browse.no.location"), ERROR),
      DOES_NOT_EXIST(AndroidBundle.message("android.wizard.module.import.source.browse.invalid.location"), ERROR),
      IS_PROJECT_OR_MODULE(AndroidBundle.message("android.wizard.module.import.source.browse.taken.location"), ERROR),
      MISSING_SUBPROJECTS(null, WARNING),
      NO_MODULES_SELECTED(AndroidBundle.message("android.wizard.module.import.source.browse.no.modules"), ERROR),
      NOT_ADT_OR_GRADLE(AndroidBundle.message("android.wizard.module.import.source.browse.cant.import"), ERROR),
      INTERNAL_ERROR(AndroidBundle.message("android.wizard.module.import.source.browse.error"), ERROR),
      VALIDATING(AndroidBundle.message("android.wizard.module.import.source.browse.validating"), null);

      @Nullable/*Not an error*/ public final MessageType severity;
      @Nullable/*No message*/ private final String message;

      ResultType(@Nullable String message, @Nullable MessageType severity) {
        this.message = message;
        this.severity = severity;
      }

      @Nullable
      public Icon getIcon() {
        return severity == null ? null : severity.getDefaultIcon();
      }

      @NotNull
      public String getMessage(@Nullable Set<String> details) {
        if (this == MISSING_SUBPROJECTS) {
          final String formattedMessage = ImportUIUtil.formatElementListString(
            details,
            AndroidBundle.message("android.wizard.module.import.source.browse.bad.modules.1"),
            AndroidBundle.message("android.wizard.module.import.source.browse.bad.modules.2"),
            AndroidBundle.message("android.wizard.module.import.source.browse.bad.modules.more"));
          return WizardUtils.toHtmlString(formattedMessage);
        }
        else {
          return Strings.nullToEmpty(message);
        }
      }
    }
  }

  private final class PrimaryModuleImportSettings implements ModuleImportSettings {
    @Override
    public boolean isModuleSelected() {
      return true;
    }

    @Override
    public void setModuleSelected(boolean selected) {
      // Do nothing - primary module
    }

    @Override
    @NotNull
    public String getModuleName() {
      return myModuleNameField.getText();
    }

    @Override
    public void setModuleName(@NotNull String moduleName) {
      if (!Objects.equal(moduleName, myModuleNameField.getText())) {
        myModuleNameField.setText(moduleName);
      }
    }

    @Override
    public void setModuleSourcePath(String relativePath) {
      // Nothing
    }

    @Override
    public void setCanToggleModuleSelection(boolean b) {
      // Nothing
    }

    @Override
    public void setCanRenameModule(boolean canRenameModule) {
      myModuleNameField.setEnabled(canRenameModule);
    }

    @Override
    public void setValidationStatus(@Nullable MessageType statusSeverity, @Nullable String statusDescription) {
      myPrimaryModuleState.setIcon(statusSeverity == null ? null : statusSeverity.getDefaultIcon());
      myPrimaryModuleState.setText(Strings.nullToEmpty(statusDescription));
    }

    @Override
    public void setVisible(boolean visible) {
      myPrimaryModuleState.setVisible(visible);
      myModuleNameField.setVisible(visible);
      myModuleNameLabel.setVisible(visible);
    }

    @Override
    public void addActionListener(@NotNull ActionListener actionListener) {
      myModuleNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          actionListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "changed"));
        }
      });
    }
  }

}

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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Step for selecting archive to import and specifying Gradle subproject name.
 */
public final class ArchiveToGradleModuleStep extends SkippableWizardStep<ArchiveToGradleModuleModel> {
  private static final Set<String> SUPPORTED_EXTENSIONS = ImmutableSet.of("jar", "aar");

  private final ListenerManager myListeners = new ListenerManager();
  private final BindingsManager myBindings = new BindingsManager();

  private ValidatorPanel myValidatorPanel;
  private JPanel myPanel;
  private JTextField myGradlePath;
  private TextFieldWithBrowseButton myArchivePath;
  private JCheckBox myRemoveOriginalFileCheckBox;

  public ArchiveToGradleModuleStep(@NotNull ArchiveToGradleModuleModel model) {
    super(model, AndroidBundle.message("android.wizard.module.import.library.title"));

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    myArchivePath.addBrowseFolderListener(AndroidBundle.message("android.wizard.module.import.library.browse.title"),
                                          AndroidBundle.message("android.wizard.module.import.library.browse.description"),
                                          model.getProject(),
                                          new FileChooserDescriptor(true, false, true, true, false, false)
                                            .withFileFilter(ArchiveToGradleModuleStep::isValidExtension));

    myBindings.bindTwoWay(new TextProperty(myArchivePath.getTextField()), model.archive());
    myBindings.bindTwoWay(new TextProperty(myGradlePath), model.gradlePath());

    // Note: model.inModule() depends on the value of model.archive(), so lets setup model.archive() first
    myListeners.receiveAndFire(model.archive(), archivePath -> model.gradlePath().set(Files.getNameWithoutExtension(archivePath)));

    SelectedProperty removeOriginal = new SelectedProperty(myRemoveOriginalFileCheckBox);
    myBindings.bind(model.moveArchive(), removeOriginal.and(model.inModule()));
    myBindings.bind(removeOriginal, model.moveArchive());
    myBindings.bind(new VisibleProperty(myRemoveOriginalFileCheckBox), model.inModule());
  }

  static boolean isValidExtension(VirtualFile file) {
    @NonNls String extension = file.getExtension();
    return extension != null && SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    ArchiveToGradleModuleModel model = getModel();
    myValidatorPanel.registerValidator(model.archive(), new ArchiveValidator());
    myValidatorPanel.registerValidator(model.gradlePath(), new GradleValidator(model.getProject()));
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myArchivePath.getTextField();
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @VisibleForTesting
  final static class ArchiveValidator implements Validator<String> {
    @NotNull
    @Override
    public Result validate(@NotNull String archive) {
      if (Strings.isNullOrEmpty(archive)) {
        return new Result(Severity.ERROR, AndroidBundle.message("android.wizard.module.import.library.no.path"));
      }

      File archiveFile = new File(archive);
      if (!archiveFile.isFile()) {
        return new Result(Severity.ERROR, AndroidBundle.message("android.wizard.module.import.library.bad.path"));
      }

      VirtualFile archiveVirtualFile = VfsUtil.findFileByIoFile(archiveFile, true);
      if (!isValidExtension(archiveVirtualFile)) {
        return new Result(Severity.ERROR, AndroidBundle.message("android.wizard.module.import.library.bad.extension"));
      }

      return Result.OK;
    }
  }

  @VisibleForTesting
  final static class GradleValidator implements Validator<String> {
    private Project myProject;

    GradleValidator(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    public Result validate(@NotNull String gradlePath) {
      if (Strings.isNullOrEmpty(gradlePath)) {
        return new Result(Severity.ERROR, AndroidBundle.message("android.wizard.module.import.library.no.name"));
      }

      int invalidCharIndex = GradleUtil.isValidGradlePath(gradlePath);
      if (invalidCharIndex >= 0) {
        return new Result(Severity.ERROR,
                          AndroidBundle.message("android.wizard.module.import.library.bad.name", gradlePath.charAt(invalidCharIndex)));
      }

      if (GradleUtil.hasModule(myProject, gradlePath)) {
        return new Result(Severity.ERROR, AndroidBundle.message("android.wizard.module.import.library.taken.name", gradlePath));
      }

      return Result.OK;
    }
  }
}

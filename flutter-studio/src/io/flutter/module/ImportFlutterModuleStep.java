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
package io.flutter.module;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.project.FlutterProjectModel;
import io.flutter.pub.PubRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

// Import an existing Flutter module into an Android app.
// TODO: It needs to edit /settings.gradle and /app/build.gradle as described in the add2app doc.
// The Project Structure editor will need to be extended to 'include' Flutter plugins for Flutter modules;
// without that users will have to edit /settings.gradle to import a Flutter plugin (which is true today).
public class ImportFlutterModuleStep extends SkippableWizardStep<FlutterProjectModel> {
  private static String DIRECTORY_NOUN = SystemInfo.isMac ? "folder" : "directory";
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final StudioWizardStepPanel myRootPanel;
  private TextFieldWithBrowseButton myFlutterModuleLocationField;
  private JPanel myPanel;

  public ImportFlutterModuleStep(FlutterProjectModel model, String title, Icon icon, FlutterProjectType type) {
    super(model, title, icon);

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    String initialLocation = WizardUtils.getProjectLocationParent().getPath();
    // Directionality matters. Let the bindings set the model's value from the text field.
    myFlutterModuleLocationField.getChildComponent().setText(initialLocation);
    TextProperty locationText = new TextProperty(myFlutterModuleLocationField.getChildComponent());
    myBindings.bind(model.projectLocation(), locationText);

    myFlutterModuleLocationField
      .addBrowseFolderListener(FlutterBundle.message("flutter.module.import.settings.location.select"), null, null,
                               FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                               TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    myValidatorPanel.registerValidator(model.projectLocation(), ImportFlutterModuleStep::validateFlutterModuleLocation);

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFlutterModuleLocationField.getTextField();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @NotNull
  private static Validator.Result validateFlutterModuleLocation(String path) {
    VirtualFile file = VfsUtil.findFileByIoFile(new File(path), true);
    if (file == null) {
      return Validator.Result.fromNullableMessage("File not found");
    }
    if (!file.isDirectory()) {
      return Validator.Result.fromNullableMessage("Please select a " + DIRECTORY_NOUN + " containing a Flutter module");
    }
    PubRoot root = PubRoot.forDirectory(file);
    if (root == null || !root.isFlutterModule()) {
      return Validator.Result.fromNullableMessage("The selected " + DIRECTORY_NOUN + " is not a Flutter module");
    }
    // TODO(messick): Check for equivalent Android releases in both module and project.
    return Validator.Result.OK;
  }
}

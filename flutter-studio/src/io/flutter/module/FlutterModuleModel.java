/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

// TODO list
// 1. Automatically perform Android framework configuraiton to add additional files to module.
// 2. Externalize strings.
// 3. Hide project type list in new project wizard.
// 4. Add ability to create Flutter project to welcome screen.
// 5. Investigage UI testing. See module android-uitests for examples.
// done 6. Add new fields (language choices, etc) to the new module definition page in the wizard.
public class FlutterModuleModel extends WizardModel {
  private static final Logger LOG = Logger.getInstance(FlutterModuleModel.class.getName());

  @NotNull private final OptionalValueProperty<String> myFlutterSdk = new OptionalValueProperty<>();
  @NotNull private final Project myProject;
  private FlutterModuleBuilder myBuilder;
  private FlutterPackageStep myModuleComponent;
  private TextProperty myModuleName;

  public FlutterModuleModel(@NotNull Project project) {
    myProject = project;
  }

  public void setBuilder(@NotNull FlutterModuleBuilder builder) {
    myBuilder = builder;
  }

  public void setModuleComponent(@NotNull FlutterPackageStep component) {
    myModuleComponent = component;
    myModuleName = new TextProperty(component.getModuleNameField());
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public TextProperty moduleName() {
    return myModuleName;
  }

  @NotNull
  public OptionalValueProperty<String> flutterSdk() {
    return myFlutterSdk;
  }

  @Override
  protected void handleFinished() {
    // Set name, module file path, content entry path.
    myModuleComponent.updateDataModel();
    try {
      // Create directories; this is not real validation.
      if (!myModuleComponent.validate()) {
        cleanupAfterValidationFailure();
        Messages.showErrorDialog("Validation error", "Internal Error");
        return;
      }
    }
    catch (ConfigurationException e) {
      // If there was a problem let the user see it to file a report.
      // Re-throwing the exception brings up an error balloon with a 'Report to Google' button.
      // Unfortunately, that button does not have any UI elements so we cannot customize the report.
      cleanupAfterValidationFailure();
      LOG.error(e);
      Messages.showErrorDialog("Configuration error (stack trace logged):\n" + e.getMessage(), "Internal Error");
      return;
    }
    if (myFlutterSdk.get().isPresent()) {
      myBuilder.setFlutterSdkPath(myFlutterSdk.get().get());
    }
    else {
      cleanupAfterValidationFailure(); // Not reached if validation is working.
      return;
    }
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    final Exception[] error = new Exception[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        myBuilder.createModule(moduleModel);
      }
      catch (ConfigurationException | IOException | ModuleWithNameAlreadyExists | JDOMException e) {
        error[0] = e;
      }
    });
    if (error[0] != null) {
      cleanupAfterValidationFailure();
      LOG.error(error[0]);
      Messages.showErrorDialog("Module creation error (stack trace logged):\n" + error[0].getMessage(), "Internal Error");
      return;
    }
    if (myBuilder.commitModule(getProject(), null) == null) {
      // A message was displayed to the user, so just clean up.
      cleanupAfterValidationFailure();
    }
  }

  private void cleanupAfterValidationFailure() {
    String filePath = myModuleComponent.getModuleContentPath().trim();
    //noinspection ResultOfMethodCallIgnored
    new File(filePath).delete();
  }
}

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.projectWizard.ModuleNameLocationComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ReflectionUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

// TODO list
// 1. (done) Stop creating 'untitled' directory when wizard is canceled.
// 2. (done) Fix layout to make the inner panel fill the outer.
// 3. (done) Make a proper module icon for the wizard selection pane.
// 4. (done) Verify ModuleNameLocationComponent fields update correctly.
// 5. (done) Fix validation for all four entries.
// 6. (done) Verify module content is created correctly.
// 7. (done) On start-up, check the Dart plugin version; that code appears to not be running.
// 8. (done) Investigate 'New Module' name. Always appears as 'Flutter' in Project Structure.
// 9. Automatically perform Android framework configuraiton to add additional files to module.
//10. Externalize strings.
public class FlutterModuleModel extends WizardModel {
  private static final Logger LOG = Logger.getInstance(FlutterModuleModel.class.getName());

  @NotNull private final OptionalValueProperty<String> myFlutterSdk = new OptionalValueProperty<>();
  @NotNull private final Project myProject;
  private FlutterModuleBuilder myBuilder;
  private ModuleNameLocationComponent myModuleComponent;
  private TextProperty myModuleName;
  private TextProperty myModuleContentRoot;
  private TextProperty myModuleFileLocation;

  public FlutterModuleModel(@NotNull Project project) {
    myProject = project;
  }

  public void setBuilder(@NotNull FlutterModuleBuilder builder) {
    myBuilder = builder;
  }

  public void setModuleComponent(@NotNull ModuleNameLocationComponent component) {
    myModuleComponent = component;
    myModuleName = new TextProperty(component.getModuleNameField());
    myModuleContentRoot = new TextProperty(fetchPrivateField("myModuleContentRoot").getTextField());
    myModuleFileLocation = new TextProperty(fetchPrivateField("myModuleFileLocation").getTextField());
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
  public TextProperty moduleContentRoot() {
    return myModuleContentRoot;
  }

  @NotNull
  public TextProperty moduleFileLocation() {
    return myModuleFileLocation;
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
    String filePath = myModuleContentRoot.get().trim();
    //noinspection ResultOfMethodCallIgnored
    new File(filePath).delete();
  }

  private TextFieldWithBrowseButton fetchPrivateField(String fieldName) {
    return ReflectionUtil // Fetching a private field.
      .getField(ModuleNameLocationComponent.class, myModuleComponent, TextFieldWithBrowseButton.class, fieldName);
  }
}

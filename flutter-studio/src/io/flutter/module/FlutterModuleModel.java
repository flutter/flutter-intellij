/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.projectWizard.ModuleNameLocationComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ReflectionUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

// TODO list
// 1. (done) Stop creating 'untitled' directory when wizard is canceled.
// 2. (done) Fix layout to make the inner panel fill the outer.
// 3. (done) Make a proper module icon for the wizard selection pane.
// 4. (done) Verify ModuleNameLocationComponent fields update correctly.
// 5. Fix validation for all four entries.
// 6. Verify module content is created correctly.
// 7. On start-up, check the Dart plugin version; that code appears to not be running.
// 8. Investigate 'New Module' name. Always appears as 'Flutter' in Project Structure.
// 9. Automatically perform Android framework configuraiton to add additional files to module.
public class FlutterModuleModel extends WizardModel {
  @NotNull private final OptionalValueProperty<String> myFlutterSdk = new OptionalValueProperty<>();
  @NotNull private final Project myProject;
  private TextProperty myModuleName;
  private FlutterModuleBuilder myBuilder;
  private ModuleNameLocationComponent myModuleComponent;

  public FlutterModuleModel(@NotNull Project project) {
    myProject = project;
  }

  public void setBuilder(FlutterModuleBuilder builder) {
    myBuilder = builder;
  }

  public void setModuleComponent(ModuleNameLocationComponent component) {
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
    myModuleComponent.updateDataModel();
    try {
      myModuleComponent.validate();
    }
    catch (ConfigurationException e) {
      // ignore it
    }
    myBuilder.setName(myModuleComponent.getModuleNameField().getText());
    String filePath = fetchPrivateField("myModuleContentRoot");
    if (filePath == null) {
      Messages.showErrorDialog("No module directory given", "Internal Error");
      return;
    }
    filePath = fetchPrivateField("myModuleFileLocation");
    if (filePath == null) {
      Messages.showErrorDialog("No module file location given", "Internal Error");
      return;
    }
    myBuilder.setModuleFilePath(
      FileUtil.toSystemIndependentName(filePath) + "/" + myBuilder.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
    if (myFlutterSdk.get().isPresent()) {
      myBuilder.setFlutterSdkPath(myFlutterSdk.get().get());
    }
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    final boolean[] error = new boolean[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        myBuilder.createModule(moduleModel);
      }
      catch (ConfigurationException | IOException | ModuleWithNameAlreadyExists | JDOMException e) {
        error[0] = true;
      }
    });
    if (error[0]) {
      return;
    }
    myBuilder.commitModule(getProject(), null);
  }

  private String fetchPrivateField(String fieldName) {
    TextFieldWithBrowseButton field =
      ReflectionUtil // Fetching a private field.
        .getField(ModuleNameLocationComponent.class, myModuleComponent, TextFieldWithBrowseButton.class, fieldName);
    return field == null ? null : field.getText().trim();
  }
}

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
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

public class FlutterModuleModel extends WizardModel {
  @NotNull private final OptionalValueProperty<String> myFlutterSdk = new OptionalValueProperty<>();
  @NotNull private final Project myProject;
  private TextProperty myModuleName;
  private FlutterModuleBuilder myBuilder;
  private ModuleNameLocationComponent myModuleComponent;
  private NamePathComponent myNameComponent;

  public FlutterModuleModel(@NotNull Project project) {
    myProject = project;
    //myFlutterSdk.addConstraint(String::trim);
  }

  public void setBuilder(FlutterModuleBuilder builder) {
    myBuilder = builder;
  }

  public void setModuleComponent(ModuleNameLocationComponent component) {
    myModuleComponent = component;
    myModuleName = new TextProperty(component.getModuleNameField());
  }

  public void setNameComponent(NamePathComponent component) {
    myNameComponent = component;
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
    myBuilder.setName(myNameComponent.getNameValue());
    myBuilder.setContentEntryPath(myNameComponent.getPath());
    TextFieldWithBrowseButton field =
      ReflectionUtil // Fetching a private field.
        .getField(ModuleNameLocationComponent.class, myModuleComponent, TextFieldWithBrowseButton.class, "myModuleFileLocation");
    String filePath;
    if (field == null) {
      filePath = myNameComponent.getPath();
    }
    else {
      filePath = field.getText().trim();
    }
    myBuilder.setModuleFilePath(
      FileUtil.toSystemIndependentName(filePath) + "/" + myBuilder.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
    if (myFlutterSdk.get().isPresent()) {
      myBuilder.setFlutterSdkPath(myFlutterSdk.get().get());
    }
    myBuilder.commitModule(getProject(), ModuleManager.getInstance(myProject).getModifiableModel());
  }
}

/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterModuleBuilder extends ModuleBuilder {

  @Override
  public String getName() {
    return getPresentableName();
  }

  @Override
  public String getPresentableName() {
    return FlutterBundle.message("flutter.module.name");
  }

  @Override
  public Icon getBigIcon() {
    return FlutterIcons.Flutter_16;
  }

  @Override
  public Icon getNodeIcon() {
    return FlutterIcons.Flutter_16;
  }

  @Override
  public void setupRootModel(ModifiableRootModel model) throws ConfigurationException {
    final ContentEntry contentEntry = doAddContentEntry(model);
    final VirtualFile baseDir = contentEntry == null ? null : contentEntry.getFile();
    if (baseDir != null) {
      setupProject(model, baseDir);
    }
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    //TODO(pq): replace with new custom wizard (or suppress useless frameworks page)
    return super.createWizardSteps(wizardContext, modulesProvider);
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    // TODO(pq): add FlutterSdkType and implement
    return super.isSuitableSdkType(sdkType);
  }

  @Override
  public boolean validate(Project current, Project dest) {

    if (FlutterSdk.getGlobalFlutterSdk() == null) {
      //TODO(pq): prompt to configure SDK.
      return false;
    }

    //TODO(pq): add validation
    return super.validate(current, dest);
  }

  @Override
  public String getParentGroup() {
    //TODO(pq): find an appropriate parent group
    return super.getParentGroup();
  }

  @Override
  public ModuleType getModuleType() {
    return FlutterModuleType.getInstance();
  }


  void setupProject(@NotNull final ModifiableRootModel model,
                    @NotNull final VirtualFile baseDir) {
    
    final FlutterSdk sdk = getFlutterSdk();
    if (sdk == null) {
      //TODO(pq): prompt to configure SDK.
      return;
    }

    // Create files.
    try {
      sdk.run(FlutterSdk.Command.CREATE, model.getModule(), baseDir, baseDir.getPath());
    }
    catch (ExecutionException e) {
      //TODO(pq): handle exceptions
      e.printStackTrace();
    }
  }

  FlutterSdk getFlutterSdk() {
    return FlutterSdk.getGlobalFlutterSdk();
  }
}

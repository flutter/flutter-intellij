/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterModuleBuilder extends ModuleBuilder {
  @Override
  public void setupRootModel(ModifiableRootModel model) throws ConfigurationException {
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

  @Nullable
  @Override
  public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
    return new FlutterModuleWizardStep();
  }

  private class FlutterModuleWizardStep extends ModuleWizardStep {

    private final FlutterGeneratorPeer myPeer;

    FlutterModuleWizardStep() {
      myPeer = new FlutterGeneratorPeer();
    }

    @Override
    public JComponent getComponent() {
      return myPeer.getComponent();
    }

    @Override
    public void updateDataModel() {
    }
  }
}

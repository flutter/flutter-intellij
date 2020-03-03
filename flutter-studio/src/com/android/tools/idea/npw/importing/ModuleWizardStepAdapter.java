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

import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Presents an Idea {@link ModuleWizardStep} based class as a {@link ModelWizardStep}
 */
public final class ModuleWizardStepAdapter extends ModelWizardStep<ModuleWizardStepAdapter.AdapterModel> {
  @Nullable static private Logger ourLog;

  private final ModuleWizardStep myWrappedStep;
  private final WizardContext myContext;

  private final BoolProperty myCanGoForward = new BoolValueProperty();

  @NotNull
  private static Logger getLog() {
    if (ourLog == null) {
      ourLog = Logger.getInstance(ModuleWizardStepAdapter.class);
    }
    return ourLog;
  }

  @TestOnly
  static void setLog(Logger testLogger) {
    ourLog = testLogger;
  }

  public ModuleWizardStepAdapter(@NotNull WizardContext context, @NotNull ModuleWizardStep toWrap) {
    super(new AdapterModel(toWrap), toWrap.getName());
    myContext = context;
    myWrappedStep = toWrap;

    myWrappedStep.registerStepListener(this::updateCanGoForward);
  }

  private void updateCanGoForward() {
    try {
      myCanGoForward.set(myWrappedStep.validate());
    }
    catch (ConfigurationException e) {
      myCanGoForward.set(false);
    }
  }

  @Override
  public void dispose() {
    myWrappedStep.disposeUIResources();
    super.dispose();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myWrappedStep.getComponent();
  }

  @Override
  protected boolean shouldShow() {
    if (ModuleImporter.getImporter(myContext).isStepVisible(myWrappedStep)) {
      return myWrappedStep.isStepVisible();
    }
    return false;
  }

  @Override
  protected void onEntering() {
    updateCanGoForward();
  }

  @Override
  protected void onProceeding() {
    myWrappedStep.updateDataModel();
    myWrappedStep.onStepLeaving();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myCanGoForward;
  }

  /**
   * Model used to trigger onWizardFinished event when Wizard completes
   */
  static class AdapterModel extends WizardModel {
    private final ModuleWizardStep myStep;

    public AdapterModel(@NotNull ModuleWizardStep step) {
      myStep = step;
    }

    @Override
    protected void handleFinished() {
      try {
        myStep.onWizardFinished();
      }
      catch (CommitStepException e) {
        getLog().error(e.getMessage());
      }
    }
  }
}
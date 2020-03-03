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
package com.android.tools.idea.npw.ideahost;

import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.npw.module.ChooseModuleTypeStep;
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep;
import com.android.tools.idea.npw.project.ConfigureAndroidSdkStep;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.google.common.base.Preconditions;
import com.intellij.ide.projectWizard.ProjectTypeStep;
import com.intellij.ide.util.newProjectWizard.WizardDelegate;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import icons.AndroidIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AndroidModuleBuilder integrates the AndroidStudio new project and new module wizards into the IDEA new project and new module wizards.
 * <p>
 * The {@link ModuleBuilder} base class integrates with the IDEA New Project and New Module UIs. AndroidModuleBuilder extends it to provide
 * an "Android" entry in the list on the left of these UIs (see
 * <a href="http://www.jetbrains.org/intellij/sdk/docs/tutorials/project_wizard/module_types.html">the IntelliJ SDK</a> for details).
 * <p>
 * If a {@link ModuleBuilder} also implements {@link WizardDelegate} then {@link ProjectTypeStep} will display the {@link ModuleWizardStep}
 * provided by {@link ModuleBuilder#getCustomOptionsStep} and then delegate the behaviour of the wizard buttons via {@link WizardDelegate}'s
 * methods. Doing this bypasses the majority of {@link ModuleBuilder}'s functionality requiring AndroidModuleBuilder to stub out a few
 * methods. AndroidModuleBuilder delegates the implementation of {@link WizardDelegate} to {@link IdeaWizardAdapter} which manages the
 * underlying {@link ModelWizard} instance.
 */
public final class AndroidModuleBuilder extends ModuleBuilder implements WizardDelegate {

  private static final String MODULE_NAME = "Android";
  private static final String MODULE_DESCRIPTION =
    "Android modules are used for developing apps to run on the <b>Android</b> operating system. An <b>Android</b> module " +
    "consists of one or more <b>Activities</b> and may support a number of form-factors including <b>Phone and Tablet</b>, <b>Wear</b> " +
    "and <b>Android Auto</b>.";

  /**
   * This adapter class hosts the Android Studio {@link ModelWizard} instance
   */
  @Nullable/*No adapter has been instantiated*/ private IdeaWizardAdapter myWizardAdapter;

  @Override
  public void setupRootModel(ModifiableRootModel modifiableRootModel) {
    // Unused. See class header.
  }

  @Override
  public String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public String getPresentableName() {
    return MODULE_NAME;
  }

  @Override
  public String getDescription() {
    return MODULE_DESCRIPTION;
  }

  @Override
  public Icon getNodeIcon() {
    return AndroidIcons.Android;
  }

  @Override
  public String getParentGroup() {
    return JavaModuleType.JAVA_GROUP;
  }

  @NotNull
  @Override
  public ModuleType getModuleType() {
    return JavaModuleType.getModuleType();
  }

  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    // Stubbed out. See class header.
    return null;
  }

  /**
   * {@inheritDoc}
   * <p>
   * This is the point where we actually provide the wizard that we are going to show. Return a wrapper around the appropriate AndroidStudio
   * wizard which presents the entire wizard as if it was a single step. This method must be called before any of the methods on the
   * {@link WizardDelegate} interface can be called.
   *
   * @param ctx Provides information about how the wizard was created (i.e. new project or new module)
   * @param parentDisposable Controls the lifetime of the wizard
   */
  @Nullable
  @Override
  public ModuleWizardStep getCustomOptionsStep(WizardContext ctx, Disposable parentDisposable) {
    if (myWizardAdapter == null) {
      createWizardAdaptor(ctx.getWizard(), ctx.isCreatingNewProject() ? WizardType.PROJECT : WizardType.MODULE, ctx.getProject());
    }

    assert myWizardAdapter != null;
    return myWizardAdapter.getProxyStep();
  }

  @Override
  public void doNextAction() {
    assert myWizardAdapter != null;
    myWizardAdapter.doNextAction();
  }

  @Override
  public void doPreviousAction() {
    assert myWizardAdapter != null;
    myWizardAdapter.doPreviousAction();
  }

  @Override
  public void doFinishAction() {
    assert myWizardAdapter != null;
    myWizardAdapter.doFinishAction();
  }

  @Override
  public boolean canProceed() {
    assert myWizardAdapter != null;
    return myWizardAdapter.canProceed();
  }

  private void createWizardAdaptor(@NotNull AbstractWizard hostWizard, @NotNull WizardType type, Project project) {
    Preconditions.checkState(myWizardAdapter == null, "Attempting to create a Wizard Adaptor when one already exists.");

    ModelWizard.Builder builder = new ModelWizard.Builder();

    if (IdeSdks.getInstance().getAndroidSdkPath() == null) {
      builder.addStep(new ConfigureAndroidSdkStep());
    }
    if (type == WizardType.PROJECT) {
      builder.addStep(new ChooseAndroidProjectStep(new NewProjectModel()));
    }
    else {
      ChooseModuleTypeStep chooseModuleTypeStep =
        ChooseModuleTypeStep.createWithDefaultGallery(project, new ProjectSyncInvoker.DefaultProjectSyncInvoker());
      builder.addStep(chooseModuleTypeStep);
    }
    myWizardAdapter = new IdeaWizardAdapter(hostWizard, builder.build());
  }

  private enum WizardType {
    PROJECT,
    MODULE
  }
}

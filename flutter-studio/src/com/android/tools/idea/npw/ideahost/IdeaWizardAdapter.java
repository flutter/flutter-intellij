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

import com.android.tools.idea.help.StudioHelpManagerImpl;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardLayout;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.ide.util.newProjectWizard.WizardDelegate;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides an implementation of the {@link WizardDelegate} interface (which plugs in to the IntelliJ IDEA New Project / Module Wizards)
 * that hosts the {@link ModelWizard} based AndroidStudio New Project and New Module wizards.
 * <p>
 * In Android Studio, wizards are hosted in the {@link ModelWizardDialog} class, however when running as a plugin in IDEA we do not create
 * the hosting dialog, but instead need to embed the wizard inside an existing dialog. This class manages that embedding. The
 * {@link WizardDelegate} class is specific to the IDEA New Project Wizard (see {@link AndroidModuleBuilder} for more details) so the
 * IdeaWizardAdapter does not need to handle the more general case of embedding any wizard (i.e. different cancellation policies etc.).
 */
final class IdeaWizardAdapter implements ModelWizard.WizardListener, WizardDelegate, Disposable {

  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final ModelWizardDialog.CustomLayout myCustomLayout = new StudioWizardLayout();
  @NotNull private final AbstractWizard myHostWizard;
  @NotNull private final ModelWizard myGuestWizard;

  IdeaWizardAdapter(@NotNull AbstractWizard host, @NotNull ModelWizard guest) {
    myHostWizard = host;

    myGuestWizard = guest;
    myGuestWizard.addResultListener(this);
    myListeners.listenAll(myGuestWizard.canGoBack(), myGuestWizard.canGoForward(), myGuestWizard.onLastStep())
      .withAndFire(this::updateButtons);

    Disposer.register(myHostWizard.getDisposable(), this);
    Disposer.register(this, myGuestWizard);
    Disposer.register(this, myCustomLayout);
  }

  /**
   * Update the buttons on the host wizard to reflect the state of the guest wizard
   */
  private void updateButtons() {
    myHostWizard.updateButtons(
      myGuestWizard.onLastStep().get(),
      myGuestWizard.canGoForward().get(),
      !myGuestWizard.canGoBack().get()
    );
  }

  @Override
  public void onWizardFinished(@NotNull ModelWizard.WizardResult result) {
    myHostWizard.close(DialogWrapper.CLOSE_EXIT_CODE, result.isFinished());
  }

  @Override
  public void onWizardAdvanceError(@NotNull Exception e) {
    DialogEarthquakeShaker.shake(myHostWizard.getWindow());
  }

  @Override
  public void doNextAction() {
    assert myGuestWizard.canGoForward().get();
    myGuestWizard.goForward();
    updateButtons();
  }

  @Override
  public void doPreviousAction() {
    assert myGuestWizard.canGoBack().get();
    myGuestWizard.goBack();
    updateButtons();
  }

  @Override
  public void doFinishAction() {
    assert myGuestWizard.canGoForward().get();
    assert myGuestWizard.onLastStep().get();
    myGuestWizard.goForward();
    updateButtons();
  }

  @Override
  public boolean canProceed() {
    return myGuestWizard.canGoForward().get();
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
    myGuestWizard.removeResultListener(this);
  }

  /**
   * @return A {@link ModuleWizardStep} that embeds the guest wizard, for use in the host wizard.
   */
  @NotNull
  public ModuleWizardStep getProxyStep() {
    return new ModuleWizardStep() {
      @Override
      public JComponent getComponent() {
        return myCustomLayout.decorate(myGuestWizard.getTitleHeader(), myGuestWizard.getContentPanel());
      }

      @Override
      public void updateDataModel() {
        // Not required as the guest wizard is using its own data model, updated via bindings.
      }

      @Nullable
      @Override
      public String getHelpId() {
        return StudioHelpManagerImpl.STUDIO_HELP_PREFIX + "studio/projects/create-project.html";
      }
    };
  }
}


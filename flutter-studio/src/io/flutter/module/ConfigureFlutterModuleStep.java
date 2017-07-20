/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class ConfigureFlutterModuleStep extends SkippableWizardStep {
  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private final ValidatorPanel myValidatorPanel;
  private JPanel myPanel;

  public ConfigureFlutterModuleStep(@NotNull NewModuleModel model,
                                    @NotNull FormFactor factor,
                                    int level,
                                    boolean library,
                                    boolean b,
                                    @NotNull String title) {
    super(model, title, factor.getIcon());
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return null;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return null;
  }
}

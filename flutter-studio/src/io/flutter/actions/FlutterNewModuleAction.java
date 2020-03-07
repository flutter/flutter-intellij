/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.flutter.module.ChooseModuleTypeStep;
import io.flutter.module.FlutterModuleModel;
import io.flutter.module.FlutterProjectType;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;

public class FlutterNewModuleAction extends AnAction implements DumbAware {

  public FlutterNewModuleAction() {
    this("New Flutter Module...");
  }

  public FlutterNewModuleAction(@NotNull String text) {
    super(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FlutterModuleModel model = new FlutterModuleModel(project, FlutterProjectType.APP);
    try {
      ModelWizard wizard = new ModelWizard.Builder()
        .addStep(new ChooseModuleTypeStep(model))
        .build();
      StudioWizardDialogBuilder builder = new StudioWizardDialogBuilder(wizard, "Create New Flutter Project");
      ModelWizardDialog dialog = builder.build();
      try {
        dialog.show();
      }
      catch (NoSuchElementException ex) {
        // This happens if no Flutter SDK is installed and the user cancels the FlutterProjectStep.
      }
    }
    catch (NoSuchMethodError x) {
      Messages.showMessageDialog("Android Studio canary is not supported", "Unsupported IDE", Messages.getErrorIcon());
    }
  }
}

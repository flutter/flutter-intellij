/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;


import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import io.flutter.module.FlutterProjectType;
import io.flutter.project.ChoseProjectTypeStep;
import io.flutter.project.FlutterProjectModel;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;

import static com.intellij.util.ReflectionUtil.getMethod;

public class FlutterNewProjectAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(FlutterNewProjectAction.class);

  public FlutterNewProjectAction() {
    this("New Flutter Project...");
  }

  public FlutterNewProjectAction(@NotNull String text) {
    super(text);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // This can be used to change the icon on the welcome screen, if needed.
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FlutterProjectModel model = new FlutterProjectModel(FlutterProjectType.APP);
    ModelWizard wizard = new ModelWizard.Builder()
      .addStep(new ChoseProjectTypeStep(model))
      .build();
    StudioWizardDialogBuilder builder = new StudioWizardDialogBuilder(wizard, "Create New Flutter Project");
    // TODO(messick) Remove this reflection call after 3.2 is stable.
    Method method = getMethod(builder.getClass(), "setUseNewUx", Boolean.class);
    if (method != null) {
      try {
        method.invoke(builder, Boolean.TRUE);
      }
      catch (IllegalAccessException | InvocationTargetException e1) {
        LOG.error(e1);
      }
    } // End of code to remove
    ModelWizardDialog dialog = builder.build();
    try {
      dialog.show();
    }
    catch (NoSuchElementException ex) {
      // This happens if no Flutter SDK is installed and the user cancels the FlutterProjectStep.
    }
  }
}

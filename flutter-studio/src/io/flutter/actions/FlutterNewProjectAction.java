/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.ide.projectWizard.ProjectCategory;
import com.intellij.ide.util.projectWizard.ModuleBuilderFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OffsetIcon;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.project.ChoseProjectTypeStep;
import io.flutter.project.FlutterProjectModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class FlutterNewProjectAction extends AnAction implements DumbAware {

  ModuleBuilderFactory[] originalModuleBuilders;
  ProjectCategory[] originalCategories;
  List<ModuleType<?>> originalTypes;
  ModuleType<?>[] originalModuleTypes;
  LinkedHashMap<ModuleType<?>, Boolean> originalModuleMap;

  public FlutterNewProjectAction() {
    super(FlutterBundle.message("action.new.project.title"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(getFlutterDecoratedIcon());
      e.getPresentation().setText(FlutterBundle.message("welcome.new.project.compact"));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!FlutterUtils.isNewAndroidStudioProjectWizard()) {
      FlutterProjectModel model = new FlutterProjectModel(FlutterProjectType.APP);
      try {
        ModelWizard wizard = new ModelWizard.Builder()
          .addStep(new ChoseProjectTypeStep(model))
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
    else {
      NewProjectWizard wizard = new NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
      NewProjectUtil.createNewProject(wizard);
    }
  }

  @NotNull
  Icon getFlutterDecoratedIcon() {
    Icon icon = AllIcons.Welcome.CreateNewProjectTab;
    Icon badgeIcon = new OffsetIcon(0, FlutterIcons.Flutter_badge).scale(0.666f);

    LayeredIcon decorated = new LayeredIcon(2);
    decorated.setIcon(badgeIcon, 0, 7, 7);
    decorated.setIcon(icon, 1, 0, 0);
    return decorated;
  }
}

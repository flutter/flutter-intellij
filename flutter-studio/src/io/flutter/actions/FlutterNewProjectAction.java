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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OffsetIcon;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterProjectType;
import io.flutter.project.ChoseProjectTypeStep;
import io.flutter.project.FlutterProjectModel;
import java.util.NoSuchElementException;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class FlutterNewProjectAction extends AnAction implements DumbAware {

  public FlutterNewProjectAction() {
    super(FlutterBundle.message("action.new.project.title"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(getFlutterDecoratedIcon());
      e.getPresentation().setText(
        Registry.is("use.tabbed.welcome.screen")
        ? FlutterBundle.message("welcome.new.project.compact")
        : FlutterBundle.message("welcome.new.project.title"));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationInfo info = ApplicationInfo.getInstance();
    int major = Integer.parseInt(info.getMajorVersion());
    int minor = Integer.parseInt(info.getMinorVersion());
    if (major == 4 && minor < 3) {
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
    } else {
      NewProjectWizard wizard = new NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
      NewProjectUtil.createNewProject(wizard);
    }
  }

  Icon getFlutterDecoratedIcon() {
    Icon icon = AllIcons.Welcome.CreateNewProject;
    Icon badgeIcon = new OffsetIcon(0, FlutterIcons.Flutter_badge).scale(0.666f);

    LayeredIcon decorated = new LayeredIcon(2);
    decorated.setIcon(badgeIcon, 0, 7, 7);
    decorated.setIcon(icon, 1, 0, 0);
    return decorated;
  }
}

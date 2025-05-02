/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;

public class FlutterNewProjectAction extends AnAction implements DumbAware {

  public FlutterNewProjectAction() {
    super(FlutterBundle.message("action.new.project.title"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setText(FlutterBundle.message("welcome.new.project.compact"));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (FlutterUtils.isAndroidStudio()) {
      System.setProperty("studio.projectview", "true");
    }
    NewProjectWizard wizard = new NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
    NewProjectUtil.createNewProject(wizard);
  }

  @Override
  @NotNull
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}

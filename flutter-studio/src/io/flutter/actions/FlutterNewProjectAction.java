/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.ide.projectWizard.ProjectTypeStep;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ReflectionUtil;

// TODO(messick): Replace this with a wizard that does not include the first step of this wizard.
public class FlutterNewProjectAction extends AnAction {

  private ProjectTypeStep myProjectTypeStep;

  public FlutterNewProjectAction() {
    super("New Flutter Project...");
  }

  private static void createNewProject(Project projectToClose, AbstractProjectWizard wizard) {
    final boolean proceed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProjectManager.getInstance().getDefaultProject(); //warm up components
    }, ProjectBundle.message("project.new.wizard.progress.title"), true, null);
    if (!proceed) return;
    if (!wizard.showAndGet()) {
      return;
    }

    final Project newProject = NewProjectUtil.createFromWizard(wizard, projectToClose);
    StartupManager.getInstance(newProject).registerPostStartupActivity(() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        // We want to show the Project view, not the Android view since it doesn't make the Dart code visible.
        ProjectView.getInstance(newProject).changeView(ProjectViewPane.ID);
      }, ModalityState.NON_MODAL);
    });
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    NewProjectWizard wizard = new NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
    ModuleWizardStep firstStep = wizard.getSequence().getAllSteps().get(0);
    myProjectTypeStep = (ProjectTypeStep)firstStep;
    // Using a private field messes up type analysis for generics. Can't be helped...
    JBList projectTypeList = fetchPrivateField("myProjectTypeList");
    CollectionListModel dataModel = (CollectionListModel)projectTypeList.getModel();
    Object flutterItem = null;
    for (Object item : dataModel.getItems()) {
      if (item.toString().equals("Flutter")) {
        flutterItem = item;
        break;
      }
    }
    // Remove all project types except Flutter. Need to verify that this does not cause a memory leak.
    if (flutterItem != null) {
      projectTypeList.setSelectedValue(flutterItem, false);
      // The list cannot be cleared because an empty list causes an NPE when the selected template is updated.
      for (Object item : dataModel.getItems().toArray()) {
        if (item != flutterItem) {
          //noinspection unchecked
          dataModel.remove(item);
        }
      }
    }
    // NewProjectUtil.createNewProject() does not return the project, so it is inlined below.
    createNewProject(getEventProject(e), wizard);
  }

  private JBList fetchPrivateField(@SuppressWarnings("SameParameterValue") String fieldName) {
    return ReflectionUtil // Fetching a private field.
      .getField(ProjectTypeStep.class, myProjectTypeStep, JBList.class, fieldName);
  }
}

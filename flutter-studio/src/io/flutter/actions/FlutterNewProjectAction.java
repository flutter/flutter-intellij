/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.ide.projectWizard.ProjectTypeStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ReflectionUtil;

public class FlutterNewProjectAction extends AnAction {

  private ProjectTypeStep myProjectTypeStep;

  public FlutterNewProjectAction() {
    super("New Flutter Project...");
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
    NewProjectUtil.createNewProject(getEventProject(e), wizard);
  }

  private JBList fetchPrivateField(@SuppressWarnings("SameParameterValue") String fieldName) {
    return ReflectionUtil // Fetching a private field.
      .getField(ProjectTypeStep.class, myProjectTypeStep, JBList.class, fieldName);
  }
}

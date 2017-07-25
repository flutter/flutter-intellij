/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetModel;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;

public class FlutterNewProjectAction extends AnAction {

  public FlutterNewProjectAction() {
    super("New Flutter Project...");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    // TODO(messick): Figure out how to eliminate all project types except Flutter.
    ModulesProvider MODULES_PROVIDER = new ModulesProvider() {
      @Override
      @NotNull
      public Module[] getModules() {
        return Module.EMPTY_ARRAY;
      }

      @Override
      public Module getModule(String name) {
        return null;
      }

      @Override
      public ModuleRootModel getRootModel(@NotNull Module module) {
        return ModuleRootManager.getInstance(module);
      }

      @Override
      public FacetModel getFacetModel(@NotNull Module module) {
        return FacetManager.getInstance(module);
      }
    };
    NewProjectWizard wizard = new NewProjectWizard(null, MODULES_PROVIDER, null);
    NewProjectUtil.createNewProject(getEventProject(e), wizard);
  }
}

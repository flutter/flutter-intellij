/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.instantapp;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MAKE_IGNORE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PROJECT_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NewInstantAppModuleModel extends WizardModel {
  @NotNull private final Project myProject;
  @NotNull private final TemplateHandle myTemplateHandle;
  @NotNull private final ProjectSyncInvoker myProjectSyncInvoker;

  @NotNull private final StringProperty myModuleName = new StringValueProperty("instantapp");
  @NotNull private final BoolProperty myCreateGitIgnore = new BoolValueProperty(true);

  public NewInstantAppModuleModel(@NotNull Project project,
                                  @NotNull TemplateHandle templateHandle,
                                  @NotNull ProjectSyncInvoker projectSyncInvoker) {
    myProject = project;
    myTemplateHandle = templateHandle;
    myProjectSyncInvoker = projectSyncInvoker;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public StringProperty moduleName() {
    return myModuleName;
  }

  @NotNull
  public BoolProperty createGitIgnore() {
    return myCreateGitIgnore;
  }

  @Override
  protected void handleFinished() {
    File moduleRoot = NewModuleModel.getModuleRoot(myProject.getBasePath(), moduleName().get());
    Map<String, Object> myTemplateValues = Maps.newHashMap();

    myTemplateValues.put(ATTR_TOP_OUT, myProject.getBasePath());
    myTemplateValues.put(ATTR_PROJECT_OUT, FileUtil.toSystemIndependentName(moduleRoot.getAbsolutePath()));
    myTemplateValues.put(ATTR_MODULE_NAME, moduleName().get());
    myTemplateValues.put(ATTR_MAKE_IGNORE, createGitIgnore().get());

    TemplateValueInjector injector = new TemplateValueInjector(myTemplateValues);
    injector.addGradleVersions(myProject);

    if (doDryRun(moduleRoot, myTemplateValues)) {
      render(moduleRoot, myTemplateValues);
    }
  }

  private boolean doDryRun(@NotNull File moduleRoot, @NotNull Map<String, Object> templateValues) {
    return renderTemplate(true, myProject, moduleRoot, templateValues, null);
  }

  private void render(@NotNull File moduleRoot, @NotNull Map<String, Object> templateValues) {
    List<File> filesToOpen = new ArrayList<>();
    boolean success = renderTemplate(false, myProject, moduleRoot, templateValues, filesToOpen);
    if (success) {
      // calling smartInvokeLater will make sure that files are open only when the project is ready
      DumbService.getInstance(myProject).smartInvokeLater(() -> TemplateUtils.openEditors(myProject, filesToOpen, true));
      myProjectSyncInvoker.syncProject(myProject);
    }
  }

  private boolean renderTemplate(boolean dryRun,
                                 @NotNull Project project,
                                 @NotNull File moduleRoot,
                                 @NotNull Map<String, Object> templateValues,
                                 @Nullable List<File> filesToOpen) {
    Template template = myTemplateHandle.getTemplate();

    // @formatter:off
    final RenderingContext context = RenderingContext.Builder.newContext(template, project)
      .withCommandName(message("android.wizard.module.new.module.menu.description"))
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(moduleRoot)
      .withParams(templateValues)
      .intoOpenFiles(filesToOpen)
      .build();
    // @formatter:on

    return template.render(context, dryRun);
  }
}

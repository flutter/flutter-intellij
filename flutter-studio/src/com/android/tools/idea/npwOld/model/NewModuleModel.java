/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npwOld.model;

import static com.android.tools.idea.npwOld.model.RenderTemplateModel.getInitialSourceLanguage;
import static com.android.tools.idea.observable.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_HAS_INSTANT_APP_WRAPPER;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_HAS_MONOLITHIC_APP_WRAPPER;
//import static com.android.tools.idea.templates.TemplateMetadata.ATTR_INSTANT_APP_PACKAGE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.npwOld.platform.Language;
import com.android.tools.idea.npwOld.template.TemplateValueInjector;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableString;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.string.StringExpression;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewModuleModel extends WizardModel {
  // Note: INVOKE_IMMEDIATELY otherwise Objects may be constructed in the wrong state
  private final BindingsManager myBindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

  @NotNull private final StringProperty myModuleName = new StringValueProperty();
  @NotNull private final StringProperty mySplitName = new StringValueProperty("feature");
  @NotNull private final BoolProperty myIsLibrary = new BoolValueProperty();
  // A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
  // module, or instead modifies an existing module (for example just adding a new Activity)
  @NotNull private final OptionalProperty<File> myTemplateFile = new OptionalValueProperty<>();
  @NotNull private final OptionalProperty<Map<String, Object>> myRenderTemplateValues = new OptionalValueProperty<>();
  @NotNull private final Map<String, Object> myTemplateValues = Maps.newHashMap();

  @NotNull private final StringProperty myApplicationName;
  @NotNull private final StringProperty myProjectLocation;
  @NotNull private final StringProperty myPackageName = new StringValueProperty();
  @NotNull private final StringProperty myProjectPackageName;
  @NotNull private final BoolProperty myIsInstantApp = new BoolValueProperty();
  @NotNull private final BoolProperty myEnableCppSupport;
  @NotNull private final OptionalValueProperty<Language> myLanguage;
  @NotNull private final OptionalProperty<Project> myProject;
  @NotNull private final ProjectSyncInvoker myProjectSyncInvoker;
  @NotNull private final MultiTemplateRenderer myMultiTemplateRenderer;
  private final boolean myCreateInExistingProject;

  { // Default init constructor
    myModuleName.addConstraint(String::trim);
    mySplitName.addConstraint(String::trim);
  }

  public NewModuleModel(@NotNull Project project,
                        @NotNull ProjectSyncInvoker projectSyncInvoker) {
    myProject = new OptionalValueProperty<>(project);
    myProjectSyncInvoker = projectSyncInvoker;
    myProjectPackageName = myPackageName;
    myCreateInExistingProject = true;
    myEnableCppSupport = new BoolValueProperty();
    myLanguage = new OptionalValueProperty<>(getInitialSourceLanguage(project));
    myApplicationName = new StringValueProperty(message("android.wizard.module.config.new.application"));
    myApplicationName.addConstraint(String::trim);
    myProjectLocation = new StringValueProperty(project.getBasePath());
    myIsLibrary.addListener(() -> updateApplicationName());
    myIsInstantApp.addListener(() -> updateApplicationName());

    myMultiTemplateRenderer = new MultiTemplateRenderer(project, projectSyncInvoker);
  }

  public NewModuleModel(@NotNull NewProjectModel projectModel, @NotNull File templateFile) {
    myProject = projectModel.project();
    myProjectPackageName = projectModel.packageName();
    myProjectSyncInvoker = projectModel.getProjectSyncInvoker();
    myCreateInExistingProject = false;
    myEnableCppSupport = projectModel.enableCppSupport();
    myApplicationName = projectModel.applicationName();
    myProjectLocation = projectModel.projectLocation();
    myTemplateFile.setValue(templateFile);
    myMultiTemplateRenderer = projectModel.getMultiTemplateRenderer();
    myMultiTemplateRenderer.incrementRenders();
    myLanguage = new OptionalValueProperty<>();

    myBindings.bind(myPackageName, myProjectPackageName, myIsInstantApp.not());
  }

  @Override
  public void dispose() {
    super.dispose();
    myBindings.releaseAll();
  }

  @NotNull
  public OptionalProperty<Project> getProject() {
    return myProject;
  }

  @NotNull
  public ProjectSyncInvoker getProjectSyncInvoker() { return myProjectSyncInvoker; }

  @NotNull
  public StringProperty applicationName() {
    return myApplicationName;
  }

  @NotNull
  public StringProperty projectLocation() {
    return myProjectLocation;
  }

  @NotNull
  public StringProperty moduleName() {
    return myModuleName;
  }

  @NotNull
  public StringProperty splitName() {
    return mySplitName;
  }

  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  @NotNull
  public BoolProperty isLibrary() {
    return myIsLibrary;
  }

  @NotNull
  public BoolProperty instantApp() {
    return myIsInstantApp;
  }

  @NotNull
  public BoolProperty enableCppSupport() {
    return myEnableCppSupport;
  }

  @NotNull
  public OptionalValueProperty<Language> language() {
    return myLanguage;
  }

  @NotNull
  public OptionalProperty<File> templateFile() {
    return myTemplateFile;
  }

  @NotNull
  public Map<String, Object> getTemplateValues() {
    return myTemplateValues;
  }

  @NotNull
  public OptionalProperty<Map<String, Object>> getRenderTemplateValues() {
    return myRenderTemplateValues;
  }

  @NotNull
  public MultiTemplateRenderer getMultiTemplateRenderer() {
    return myMultiTemplateRenderer;
  }

  @NotNull
  public ObservableString computedFeatureModulePackageName() {
    return new StringExpression(myProjectPackageName, mySplitName) {

      @NotNull
      @Override
      public String get() {
        return myProjectPackageName.get() + "." + mySplitName.get();
      }
    };
  }

  /**
   * This method should be called if there is no "Activity Render Template" step (For example when creating a Library, or the activity
   * creation is skipped by the user)
   */
  public void setDefaultRenderTemplateValues(@NotNull RenderTemplateModel renderModel, @Nullable Project project) {
    Map<String, Object> renderTemplateValues = Maps.newHashMap();
    new TemplateValueInjector(renderTemplateValues)
      .setBuildVersion(renderModel.androidSdkInfo().getValue(), project)
      .setModuleRoots(renderModel.getTemplate().get().getPaths(), project.getBasePath(), moduleName().get(), packageName().get());

    getRenderTemplateValues().setValue(renderTemplateValues);
  }

  @NotNull
  public static File getModuleRoot(@NotNull String projectLocation, @NotNull String moduleName) {
    // Module names may use ":" for sub folders. This mapping is only true when creating new modules, as the user can later customize
    // the Module Path (called Project Path in gradle world) in "settings.gradle"
    moduleName = moduleName.replace(':', File.separatorChar);
    return new File(projectLocation, moduleName);
  }

  @Override
  protected void handleFinished() {
    myMultiTemplateRenderer.requestRender(new ModuleTemplateRenderer());
  }

  @Override
  protected void handleSkipped() {
    myMultiTemplateRenderer.skipRender();
  }

  private class ModuleTemplateRenderer implements MultiTemplateRenderer.TemplateRenderer {
    Map<String, Object> myTemplateValues;

    @Override
    public boolean doDryRun() {
      if (myTemplateFile.getValueOrNull() == null) {
        return false; // If here, the user opted to skip creating any module at all, or is just adding a new Activity
      }

      // By the time we run handleFinished(), we must have a Project
      if (!myProject.get().isPresent()) {
        getLog().error("NewModuleModel did not collect expected information and will not complete. Please report this error.");
        return false;
      }

      Map<String, Object> renderTemplateValues = myRenderTemplateValues.getValueOrNull();

      myTemplateValues = new HashMap<>(NewModuleModel.this.myTemplateValues);
      myTemplateValues.put(ATTR_IS_LIBRARY_MODULE, myIsLibrary.get());

      Project project = myProject.getValue();
      //if (myIsInstantApp.get()) {
      //  myTemplateValues.put(ATTR_INSTANT_APP_PACKAGE_NAME, myProjectPackageName.get());
      //
      //  if (renderTemplateValues != null) {
      //    new TemplateValueInjector(renderTemplateValues)
      //      .setInstantAppSupport(myCreateInExistingProject, project, myModuleName.get());
      //  }
      //
      //  if (myCreateInExistingProject) {
      //    boolean hasInstantAppWrapper = myIsInstantApp.get() && InstantApps.findBaseFeature(project) == null;
      //    myTemplateValues.put(ATTR_HAS_MONOLITHIC_APP_WRAPPER, false);
      //    myTemplateValues.put(ATTR_HAS_INSTANT_APP_WRAPPER, hasInstantAppWrapper);
      //  }
      //}

      if (renderTemplateValues != null) {
        if (language().get().isPresent()) { // For new Projects, we have a different UI, so no Language should be present
          new TemplateValueInjector(renderTemplateValues).setLanguage(language().getValue());
        }
        myTemplateValues.putAll(renderTemplateValues);
      }

      // returns false if there was a render conflict and the user chose to cancel creating the template
      return renderModule(true, myTemplateValues, project, myModuleName.get());
    }

    @Override
    public void render() {
      Project project = myProject.getValue();
      boolean success = new WriteCommandAction<Boolean>(project, "New Module") {
        @Override
        protected void run(@NotNull Result<Boolean> result) {
          result.setResult(renderModule(false, myTemplateValues, project, myModuleName.get()));
        }
      }.execute().getResultObject();

      if (!success) {
        getLog().warn("A problem occurred while creating a new Module. Please check the log file for possible errors.");
      }
    }

    private boolean renderModule(boolean dryRun, @NotNull Map<String, Object> templateState, @NotNull Project project,
                                 @NotNull String moduleName) {
      File projectRoot = new File(project.getBasePath());
      File moduleRoot = getModuleRoot(project.getBasePath(), moduleName);
      Template template = Template.createFromPath(myTemplateFile.getValue());
      List<File> filesToOpen = new ArrayList<>();

      // @formatter:off
      RenderingContext context = RenderingContext.Builder.newContext(template, project)
        .withCommandName("New Module")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .intoOpenFiles(filesToOpen)
        .withParams(templateState)
        .build();
      // @formatter:on

      boolean renderResult = template.render(context, dryRun);
      if (renderResult && !dryRun) {
        // calling smartInvokeLater will make sure that files are open only when the project is ready
        DumbService.getInstance(project).smartInvokeLater(() -> TemplateUtils.openEditors(project, filesToOpen, false));
      }

      return renderResult;
    }
  }

  private void updateApplicationName() {
    String msgId;
    if (myIsInstantApp.get()) {
      //boolean isNewBaseFeature = myProject.get().isPresent() && InstantApps.findBaseFeature(myProject.getValue()) == null;
      //msgId = isNewBaseFeature ? "android.wizard.module.config.new.base.feature": "android.wizard.module.config.new.feature";
      msgId = "android.wizard.module.config.new.library"; //  not reached
    }
    else {
      msgId = myIsLibrary.get() ? "android.wizard.module.config.new.library" : "android.wizard.module.config.new.application";
    }
    myApplicationName.set(message(msgId));
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(NewModuleModel.class);
  }
}

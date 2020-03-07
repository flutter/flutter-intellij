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
package com.android.tools.idea.npwOld.template;


import static org.jetbrains.android.refactoring.MigrateToAndroidxUtil.isAndroidx;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.npwOld.FormFactor;
import com.android.tools.idea.npwOld.model.NewModuleModel;
import com.android.tools.idea.npwOld.model.RenderTemplateModel;
import com.android.tools.idea.npwOld.platform.AndroidVersionsInfo;
import com.android.tools.idea.npwOld.project.AndroidPackageUtils;
import com.android.tools.idea.npwOld.ui.ActivityGallery;
import com.android.tools.idea.npwOld.ui.WizardGallery;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IconUtil;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This step allows the user to select which type of Activity they want to create.
 *
 * TODO: ATTR_IS_LAUNCHER seems to be dead code, it was one option in the old UI flow. Find out if we can remove it.
 * TODO: This class and ChooseModuleTypeStep looks to have a lot in common. Should we have something more specific than a ASGallery,
 * that renders "Gallery items"?
 */
public class ChooseActivityTypeStep extends SkippableWizardStep<NewModuleModel> {
  private final RenderTemplateModel myRenderModel;
  private @NotNull List<TemplateRenderer> myTemplateRenderers;
  private @NotNull List<NamedModuleTemplate> myModuleTemplates;

  private @NotNull ASGallery<TemplateRenderer> myActivityGallery;
  private @NotNull ValidatorPanel myValidatorPanel;
  private final StringProperty myInvalidParameterMessage = new StringValueProperty();
  private final ListenerManager myListeners = new ListenerManager();

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull FormFactor formFactor,
                                @NotNull List<NamedModuleTemplate> moduleTemplates) {
    this(moduleModel, renderModel, formFactor);
    init(formFactor, moduleTemplates);
  }

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull FormFactor formFactor,
                                @NotNull VirtualFile targetDirectory) {
    this(moduleModel, renderModel, formFactor);
    List<NamedModuleTemplate> moduleTemplates = AndroidPackageUtils.getModuleTemplates(renderModel.getAndroidFacet(), targetDirectory);
    init(formFactor, moduleTemplates);
  }

  private ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel, @NotNull RenderTemplateModel renderModel,
                                 @NotNull FormFactor formFactor) {
    super(moduleModel, message("android.wizard.activity.add", formFactor.id), formFactor.getIcon());
    this.myRenderModel = renderModel;
  }

  private void init(@NotNull FormFactor formFactor,
                    @NotNull List<NamedModuleTemplate> moduleTemplates) {
    myModuleTemplates = moduleTemplates;
    List<TemplateHandle> templateHandles = new ArrayList<>();//TemplateManager.getInstance().getTemplateList(formFactor);

    myTemplateRenderers = Lists.newArrayListWithExpectedSize(templateHandles.size() + 1);  // Extra entry for "Add No Activity" template
    if (isNewModule()) {
      myTemplateRenderers.add(new TemplateRenderer(null)); // New modules need a "Add No Activity" entry
    }
    for (TemplateHandle templateHandle : templateHandles) {
      myTemplateRenderers.add(new TemplateRenderer(templateHandle));
    }

    myActivityGallery = new WizardGallery<>(
      getTitle(),
      galEntry -> galEntry == null ? null : galEntry.getImage() == null ? null : IconUtil.createImageIcon(galEntry.getImage()),
      galEntry -> galEntry == null ? message("android.wizard.gallery.item.none") : galEntry.getLabel());
    myValidatorPanel = new ValidatorPanel(this, new JBScrollPane(myActivityGallery));
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myActivityGallery;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    String title = message("android.wizard.config.activity.title");
    return Lists.newArrayList(new ConfigureTemplateParametersStep(myRenderModel, title, myModuleTemplates));
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myValidatorPanel.registerMessageSource(myInvalidParameterMessage);

    myActivityGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myActivityGallery.addListSelectionListener(listSelectionEvent -> {
      TemplateRenderer selectedTemplate = myActivityGallery.getSelectedElement();
      if (selectedTemplate != null) {
        myRenderModel.setTemplateHandle(selectedTemplate.getTemplate());
        wizard.updateNavigationProperties();
      }
      validateTemplate();
    });

    myListeners.listenAndFire(getModel().enableCppSupport().or(getModel().instantApp()), src -> {
      TemplateRenderer[] listItems = createGalleryList(myTemplateRenderers);
      myActivityGallery.setModel(JBList.createDefaultListModel((Object[])listItems));
      myActivityGallery.setSelectedIndex(getDefaultSelectedTemplateIndex(listItems, isNewModule())); // Also fires the Selection Listener
    });
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onEntering() {
    validateTemplate();
  }

  @Override
  protected void onProceeding() {
    // TODO: From David: Can we look into moving this logic into handleFinished?
    // There should be multiple hashtables that a model points to, which gets merged at the last second. That way, we can clear one of the
    // hashtables.

    NewModuleModel moduleModel = getModel();
    Project project = moduleModel.getProject().getValueOrNull();
    if (myRenderModel.getTemplateHandle() == null) { // "Add No Activity" selected
      moduleModel.setDefaultRenderTemplateValues(myRenderModel, project);
    }
    else {
      moduleModel.getRenderTemplateValues().setValue(myRenderModel.getTemplateValues());
    }

    new TemplateValueInjector(moduleModel.getTemplateValues())
      .setProjectDefaults(project, moduleModel.applicationName().get());
  }

  private static int getDefaultSelectedTemplateIndex(@NotNull TemplateRenderer[] templateRenderers, boolean isNewModule) {
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getLabel().equals("Empty Activity")) {
        return i;
      }
    }

    // Default template not found. Instead, return the index to the first valid template renderer (e.g. skip "Add No Activity", etc.)
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getTemplate() != null) {
        return i;
      }
    }

    assert false; // "We should never get here - there should always be at least one valid template
    return 0;
  }

  private boolean isNewModule() {
    return myRenderModel.getModule() == null;
  }

  private TemplateRenderer[] createGalleryList(@NotNull List<TemplateRenderer> templateRenderers) {
    // Cpp and Iapp Templates are mutually exclusive, and Cpp take priority
    Predicate<TemplateRenderer> predicate;
    if (getModel().enableCppSupport().get()) {
      predicate = TemplateRenderer::isCppTemplate;
    }
    else if (getModel().instantApp().get()) {
      predicate = TemplateRenderer::isIappTemplate;
    }
    else {
      predicate = templateRenderer -> true;
    }

    List<TemplateRenderer> filteredTemplates = templateRenderers.stream().filter(predicate).collect(Collectors.toList());
    if (filteredTemplates.size() > 1) {
      return filteredTemplates.toArray(new TemplateRenderer[0]);
    }

    return templateRenderers.toArray(new TemplateRenderer[0]);
  }


  /**
   * See also {@link com.android.tools.idea.actions.NewAndroidComponentAction#update}
   */
  private void validateTemplate() {
    TemplateHandle template = myRenderModel.getTemplateHandle();
    TemplateMetadata templateData = (template == null) ? null : template.getMetadata();
    AndroidVersionsInfo.VersionItem androidSdkInfo = myRenderModel.androidSdkInfo().getValueOrNull();
    AndroidFacet facet = myRenderModel.getAndroidFacet();

    // Start by assuming API levels are great enough for the Template
    int moduleApiLevel = Integer.MAX_VALUE, moduleBuildApiLevel = Integer.MAX_VALUE;
    if (androidSdkInfo != null) {
      moduleApiLevel = androidSdkInfo.getMinApiLevel();
      moduleBuildApiLevel = androidSdkInfo.getBuildApiLevel();
    }
    else if (facet != null) {
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(facet);
      moduleApiLevel = moduleInfo.getMinSdkVersion().getFeatureLevel();
      if (moduleInfo.getBuildSdkVersion() != null) {
        moduleBuildApiLevel = moduleInfo.getBuildSdkVersion().getFeatureLevel();
      }
    }

    Project project = getModel().getProject().getValueOrNull();
    boolean isAndroidxProj = project != null &&  isAndroidx(project);
    myInvalidParameterMessage.set(validateTemplate(templateData, moduleApiLevel, moduleBuildApiLevel, isNewModule(), isAndroidxProj));
  }

  @NotNull
  @VisibleForTesting
  static String validateTemplate(@Nullable TemplateMetadata template,
                                 int moduleApiLevel,
                                 int moduleBuildApiLevel,
                                 boolean isNewModule,
                                 boolean isAndroidxProj) {
    if (template == null) {
      return isNewModule ? "" : message("android.wizard.activity.not.found");
    }

    if (moduleApiLevel < template.getMinSdk()) {
      return message("android.wizard.activity.invalid.min.sdk", template.getMinSdk());
    }

    if (moduleBuildApiLevel < template.getMinBuildApi()) {
      return message("android.wizard.activity.invalid.min.build", template.getMinBuildApi());
    }

    if (template.getAndroidXRequired() && !isAndroidxProj) {
      return message("android.wizard.activity.invalid.androidx");
    }

    return "";
  }

  private static class TemplateRenderer {
    @Nullable private final TemplateHandle myTemplate;

    TemplateRenderer(@Nullable TemplateHandle template) {
      this.myTemplate = template;
    }

    @Nullable
    TemplateHandle getTemplate() {
      return myTemplate;
    }

    @NotNull
    String getLabel() {
      return ActivityGallery.getTemplateImageLabel(myTemplate, false);
    }

    @Override
    public String toString() {
      return getLabel();
    }

    boolean isCppTemplate() {
      if (myTemplate == null) {
        return true;
      }

      // TODO: This is not a good way to find Cpp templates. However, the cpp design needs to be reviewed, and probably updated.
      // TODO: 1 - The Cpp check-box is at the project level, but should probably be at the Module level (like instant apps)
      // TODO: 2 - We should have a dedicated list for Cpp files, or at least add a specific flag to the Templates that are allowed.
      String title = myTemplate.getMetadata().getTitle();
      return "Empty Activity".equals(title) || "Basic Activity".equals(title);
    }

    boolean isIappTemplate() {
      // TODO: See comments for #isCppTemplate()
      return !"Settings Activity".equals(getLabel());
    }

    /**
     * Return the image associated with the current template, if it specifies one, or null otherwise.
     */
    @Nullable
    Image getImage() {
      return ActivityGallery.getTemplateImage(myTemplate, false);
    }
  }
}

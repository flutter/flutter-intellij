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

import static com.android.tools.idea.npw.model.NewProjectModel.getSuggestedProjectPackage;
import static com.android.tools.idea.npw.ui.ActivityGallery.getTemplateImage;
import static com.android.tools.idea.templates.Template.ANDROID_MODULE_TEMPLATE;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.module.ConfigureAndroidModuleStep;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.ModuleTemplateGalleryEntry;
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.project.Project;
import java.awt.Image;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewInstantAppModuleDescriptionProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleGalleryEntry> getDescriptions(Project project) {
    if(StudioFlags.UAB_HIDE_INSTANT_MODULES_FOR_NON_FEATURE_PLUGIN_PROJECTS.get() &&
       !AndroidGradleModuleUtils.projectContainsFeatureModule(project)) {
      return Arrays.asList();
    }
    return Arrays.asList(
      new FeatureTemplateGalleryEntry(),
      new ApplicationTemplateGalleryEntry());
  }

  private static class FeatureTemplateGalleryEntry implements ModuleTemplateGalleryEntry {
    @NotNull private final TemplateHandle myTemplateHandle;

    FeatureTemplateGalleryEntry() {
      String moduleName = message("android.wizard.module.new.feature.module");
      myTemplateHandle = new TemplateHandle(TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, moduleName));
    }

    @Nullable
    @Override
    public Image getIcon() {
      return getTemplateImage(myTemplateHandle, false);
    }

    @NotNull
    @Override
    public String getName() {
      return myTemplateHandle.getMetadata().getTitle();
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplateHandle.getMetadata().getDescription();
    }

    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public File getTemplateFile() {
      return TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_MODULE_TEMPLATE);
    }

    @NotNull
    @Override
    public FormFactor getFormFactor() {
      return FormFactor.MOBILE;
    }

    @Override
    public boolean isLibrary() {
      return true;
    }

    @Override
    public boolean isInstantApp() {
      return true;
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      String basePackage = getSuggestedProjectPackage(model.getProject().getValue(), true);
      return new ConfigureAndroidModuleStep(model, FormFactor.MOBILE, myTemplateHandle.getMetadata().getMinSdk(), basePackage, true, true,
                                            getDescription());
    }
  }

  private static class ApplicationTemplateGalleryEntry implements ModuleGalleryEntry {
    @NotNull private TemplateHandle myTemplateHandle;

    ApplicationTemplateGalleryEntry() {
      myTemplateHandle = new TemplateHandle(TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, "Instant App"));
    }

    @Nullable
    @Override
    public Image getIcon() {
      return getTemplateImage(myTemplateHandle, false);
    }

    @NotNull
    @Override
    public String getName() {
      return myTemplateHandle.getMetadata().getTitle();
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplateHandle.getMetadata().getDescription();
    }

    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new ConfigureInstantAppModuleStep(new NewInstantAppModuleModel(model.getProject().getValue(), myTemplateHandle, model.getProjectSyncInvoker()), getName());
    }
  }
}

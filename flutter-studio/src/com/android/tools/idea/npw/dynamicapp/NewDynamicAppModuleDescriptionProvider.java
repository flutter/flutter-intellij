/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.dynamicapp;

import static com.android.tools.idea.npw.model.NewProjectModel.getSuggestedProjectPackage;
import static com.android.tools.idea.npw.ui.ActivityGallery.getTemplateImage;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.ModuleTemplateGalleryEntry;
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import java.awt.Image;
import java.io.File;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewDynamicAppModuleDescriptionProvider implements ModuleDescriptionProvider {
  public static final String DYNAMIC_FEATURE_TEMPLATE = "Dynamic Feature";
  public static final String INSTANT_DYNAMIC_FEATURE_TEMPLATE = "Dynamic Feature (Instant App)";

  @Override
  public Collection<ModuleGalleryEntry> getDescriptions(Project project) {
    if(StudioFlags.UAB_INSTANT_DYNAMIC_FEATURE_MODULE.get() && !hasFeaturePlugin(project)) {
      return ImmutableList.of(
        new FeatureTemplateGalleryEntry(false),
        new FeatureTemplateGalleryEntry(true)
      );
    } else {
      return ImmutableList.of(
        new FeatureTemplateGalleryEntry(false)
      );
    }
  }

  private boolean hasFeaturePlugin(Project project) {
    return StudioFlags.UAB_HIDE_INSTANT_MODULES_FOR_NON_FEATURE_PLUGIN_PROJECTS.get() && AndroidGradleModuleUtils
      .projectContainsFeatureModule(project);
  }

  private static class FeatureTemplateGalleryEntry implements ModuleTemplateGalleryEntry {
    @NotNull private final File myTemplateFile;
    @NotNull private TemplateHandle myTemplateHandle;
    private boolean myIsInstant;

    FeatureTemplateGalleryEntry(boolean isInstant) {
      myTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, isInstant ? INSTANT_DYNAMIC_FEATURE_TEMPLATE :
                                                                                           DYNAMIC_FEATURE_TEMPLATE);
      myTemplateHandle = new TemplateHandle(myTemplateFile);
      myIsInstant = isInstant;
    }

    @Nullable
    @Override
    public Image getIcon() {
      return getTemplateImage(myTemplateHandle, false);
    }

    @NotNull
    @Override
    public String getName() {
      return message(myIsInstant ? "android.wizard.module.new.dynamic.module.instant" :
                     "android.wizard.module.new.dynamic.module");
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
      return myTemplateFile;
    }

    @NotNull
    @Override
    public FormFactor getFormFactor() {
      return FormFactor.MOBILE;
    }

    @Override
    public boolean isLibrary() {
      return false;
    }

    @Override
    public boolean isInstantApp() {
      return false;
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      Project project = model.getProject().getValue();
      String basePackage = getSuggestedProjectPackage(project, false);
      return new ConfigureDynamicModuleStep(new DynamicFeatureModel(project, myTemplateHandle, model.getProjectSyncInvoker()), basePackage, myIsInstant);
    }
  }
}

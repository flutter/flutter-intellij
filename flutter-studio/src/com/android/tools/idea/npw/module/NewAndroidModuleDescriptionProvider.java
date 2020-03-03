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
package com.android.tools.idea.npw.module;

import static com.android.tools.idea.npw.model.NewProjectModel.getSuggestedProjectPackage;
import static com.android.tools.idea.npw.ui.ActivityGallery.getTemplateImage;
import static com.android.tools.idea.templates.Template.ANDROID_PROJECT_TEMPLATE;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.project.Project;
import java.awt.Image;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewAndroidModuleDescriptionProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleTemplateGalleryEntry> getDescriptions(Project project) {
    ArrayList<ModuleTemplateGalleryEntry> res = new ArrayList<>();

    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }

      int minSdk = metadata.getMinSdk();
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == FormFactor.CAR) {
        // Auto is not a standalone module (but rather a modification to a mobile module)
      }
      else if (formFactor == FormFactor.GLASS && !AndroidSdkUtils.isGlassInstalled()) {
        // Hidden if not installed
      }
      else if (formFactor.equals(FormFactor.MOBILE)) {
        res.add(new AndroidModuleTemplateGalleryEntry(templateFile, formFactor, minSdk, false, getModuleTypeIcon(templateFile),
                                                      message("android.wizard.module.new.mobile"), metadata.getTitle()));

        File androidProjectTemplate = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_PROJECT_TEMPLATE);
        res.add(new AndroidModuleTemplateGalleryEntry(templateFile, formFactor, minSdk, true, getModuleTypeIcon(androidProjectTemplate),
                                                      message("android.wizard.module.new.library"), metadata.getDescription()));
      }
      else {
        res.add(new AndroidModuleTemplateGalleryEntry(templateFile, formFactor, minSdk, false, getModuleTypeIcon(templateFile),
                                                      metadata.getTitle(), metadata.getDescription()));
      }
    }

    return res;
  }

  private static Image getModuleTypeIcon(@NotNull File templateFile) {
    return getTemplateImage(new TemplateHandle(templateFile), false);
  }

  private static class AndroidModuleTemplateGalleryEntry implements ModuleTemplateGalleryEntry {
    private final File myTemplateFile;
    private final FormFactor myFormFactor;
    private final int myMinSdkLevel;
    private final boolean myIsLibrary;
    private final Image myIcon;
    private final String myName;
    private final String myDescription;

    AndroidModuleTemplateGalleryEntry(File templateFile, FormFactor formFactor, int minSdkLevel, boolean isLibrary,
                                      Image icon, String name, String description) {
      this.myTemplateFile = templateFile;
      this.myFormFactor = formFactor;
      this.myMinSdkLevel = minSdkLevel;
      this.myIsLibrary = isLibrary;
      this.myIcon = icon;
      this.myName = name;
      this.myDescription = description;
    }

    @NotNull
    @Override
    public File getTemplateFile() {
      return myTemplateFile;
    }

    @NotNull
    @Override
    public FormFactor getFormFactor() {
      return myFormFactor;
    }

    @Override
    public boolean isLibrary() {
      return myIsLibrary;
    }

    @Override
    public boolean isInstantApp() {
      return false;
    }

    @Nullable
    @Override
    public Image getIcon() {
      return myIcon;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public String getDescription() {
      return myDescription;
    }

    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      String basePackage = getSuggestedProjectPackage(model.getProject().getValue(), false);
      return new ConfigureAndroidModuleStep(model, myFormFactor, myMinSdkLevel, basePackage, isLibrary(), false, myName);
    }
  }
}

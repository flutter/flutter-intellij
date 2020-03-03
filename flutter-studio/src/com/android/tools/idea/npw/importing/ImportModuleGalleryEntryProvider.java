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
package com.android.tools.idea.npw.importing;

import static com.android.tools.idea.npw.ui.ActivityGallery.getTemplateImage;
import static com.android.tools.idea.templates.Template.ANDROID_PROJECT_TEMPLATE;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
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

public class ImportModuleGalleryEntryProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleGalleryEntry> getDescriptions(Project project) {


    return ImmutableList.of(
      new SourceImportModuleGalleryEntry(message("android.wizard.module.import.eclipse.title")),
      new SourceImportModuleGalleryEntry(message("android.wizard.module.import.gradle.title")),
      new ArchiveImportModuleGalleryEntry()
    );
  }

  private static class SourceImportModuleGalleryEntry implements ModuleGalleryEntry {

    @NotNull
    private final TemplateHandle myTemplateHandle;

    SourceImportModuleGalleryEntry(String templateName) {
      myTemplateHandle = new TemplateHandle(TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, templateName));
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

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new SourceToGradleModuleStep(new SourceToGradleModuleModel(model.getProject().getValue(), model.getProjectSyncInvoker()));
    }
  }

  private static class ArchiveImportModuleGalleryEntry implements ModuleGalleryEntry {

    @Nullable
    @Override
    public Image getIcon() {
      File androidModuleTemplate = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_PROJECT_TEMPLATE);
      return  getTemplateImage(new TemplateHandle(androidModuleTemplate), false);
    }

    @NotNull
    @Override
    public String getName() {
      return message("android.wizard.module.import.title");
    }

    @Nullable
    @Override
    public String getDescription() {
      return message("android.wizard.module.import.description");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new ArchiveToGradleModuleStep(new ArchiveToGradleModuleModel(model.getProject().getValue(), model.getProjectSyncInvoker()));
    }
  }
}

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
package com.android.tools.idea.npwOld.java;

import static com.android.tools.idea.npwOld.ui.ActivityGallery.getTemplateImage;

import com.android.tools.idea.npwOld.model.NewModuleModel;
import com.android.tools.idea.npwOld.module.ModuleDescriptionProvider;
import com.android.tools.idea.npwOld.module.ModuleGalleryEntry;
import com.android.tools.idea.npwOld.template.TemplateHandle;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.project.Project;
import java.awt.Image;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewJavaModuleDescriptionProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleGalleryEntry> getDescriptions(Project project) {
    return Collections.singletonList(new JavaModuleTemplateGalleryEntry());
  }

  private static class JavaModuleTemplateGalleryEntry implements ModuleGalleryEntry {
    @NotNull private TemplateHandle myTemplateHandle;

    JavaModuleTemplateGalleryEntry() {
      myTemplateHandle = new TemplateHandle(TemplateManager.getInstance().getTemplateFile(Template.CATEGORY_APPLICATION, "Java Library"));
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
      return new ConfigureJavaModuleStep(new NewJavaModuleModel(model.getProject().getValue(), myTemplateHandle, model.getProjectSyncInvoker()), getName());
    }
  }
}

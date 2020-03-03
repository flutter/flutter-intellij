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

import static java.util.stream.Collectors.toMap;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.npw.ui.WizardGallery;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This step allows the user to select which type of module they want to create.
 */
public class ChooseModuleTypeStep extends ModelWizardStep.WithoutModel {
  public static final String ANDROID_WEAR_MODULE_NAME = "Wear OS Module";
  public static final String ANDROID_TV_MODULE_NAME = "Android TV Module";
  public static final String ANDROID_THINGS_MODULE_NAME = "Android Things Module";
  public static final String JAVA_LIBRARY_MODULE_NAME = "Java Library";
  public static final String GOOGLE_CLOUD_MODULE_NAME = "Google Cloud Module";

  private final List<ModuleGalleryEntry> myModuleGalleryEntryList;
  private final ProjectSyncInvoker myProjectSyncInvoker;
  private final JComponent myRootPanel;
  private final Project myProject;

  private ASGallery<ModuleGalleryEntry> myFormFactorGallery;
  private Map<ModuleGalleryEntry, SkippableWizardStep> myModuleDescriptionToStepMap;

  public ChooseModuleTypeStep(@NotNull Project project,
                              @NotNull List<ModuleGalleryEntry> moduleGalleryEntries,
                              @NotNull ProjectSyncInvoker projectSyncInvoker) {
    super(message("android.wizard.module.new.module.header"));

    myProject = project;
    myModuleGalleryEntryList = sortModuleEntries(moduleGalleryEntries);
    myProjectSyncInvoker = projectSyncInvoker;
    myRootPanel = createGallery();
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  public static ChooseModuleTypeStep createWithDefaultGallery(Project project, ProjectSyncInvoker projectSyncInvoker) {
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    for (ModuleDescriptionProvider provider : ModuleDescriptionProvider.EP_NAME.getExtensions()) {
      moduleDescriptions.addAll(provider.getDescriptions(project));
    }
    return new ChooseModuleTypeStep(project, moduleDescriptions, projectSyncInvoker);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    List<ModelWizardStep> allSteps = Lists.newArrayList();
    myModuleDescriptionToStepMap = new HashMap<>();
    for (ModuleGalleryEntry moduleGalleryEntry : myModuleGalleryEntryList) {
      NewModuleModel model = new NewModuleModel(myProject, myProjectSyncInvoker);
      if (moduleGalleryEntry instanceof ModuleTemplateGalleryEntry) {
        ModuleTemplateGalleryEntry templateEntry =  (ModuleTemplateGalleryEntry) moduleGalleryEntry;
        model.isLibrary().set(templateEntry.isLibrary());
        model.instantApp().set(templateEntry.isInstantApp());
        model.templateFile().setValue(templateEntry.getTemplateFile());
      }

      SkippableWizardStep step = moduleGalleryEntry.createStep(model);
      allSteps.add(step);
      myModuleDescriptionToStepMap.put(moduleGalleryEntry, step);
    }

    return allSteps;
  }

  @NotNull
  private JComponent createGallery() {
    myFormFactorGallery = new WizardGallery<>(
      getTitle(),
      galEntry -> galEntry.getIcon() == null ? null : galEntry.getIcon(),
      galEntry -> galEntry == null ? message("android.wizard.gallery.item.none") : galEntry.getName()
    );

    return new JBScrollPane(myFormFactorGallery);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myFormFactorGallery.setModel(JBList.createDefaultListModel(myModuleGalleryEntryList.toArray()));
    myFormFactorGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myFormFactorGallery.setSelectedIndex(0);
  }

  @Override
  protected void onProceeding() {
    // This wizard includes a step for each module, but we only visit the selected one. First, we hide all steps (in case we visited a
    // different module before and hit back), and then we activate the step we care about.
    ModuleGalleryEntry selectedEntry = myFormFactorGallery.getSelectedElement();
    myModuleDescriptionToStepMap.forEach((galleryEntry, step) -> step.setShouldShow(galleryEntry == selectedEntry));
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFormFactorGallery;
  }

  @VisibleForTesting
  @NotNull
  static List<ModuleGalleryEntry> sortModuleEntries(@NotNull List<ModuleGalleryEntry> moduleTypesProviders) {
    // To have a sequence specified by design, we hardcode the sequence. Everything else is added at the end (sorted by name)
    String[] orderedNames = {
      message("android.wizard.module.new.mobile"), message("android.wizard.module.new.library"),
      message("android.wizard.module.new.dynamic.module"),
      message("android.wizard.module.new.dynamic.module.instant"),
      message("android.wizard.module.new.instant.app"),
      message("android.wizard.module.new.feature.module"), ANDROID_WEAR_MODULE_NAME, ANDROID_TV_MODULE_NAME,
      ANDROID_THINGS_MODULE_NAME, message("android.wizard.module.import.gradle.title"),
      message("android.wizard.module.import.eclipse.title"), message("android.wizard.module.import.title"),
      JAVA_LIBRARY_MODULE_NAME, GOOGLE_CLOUD_MODULE_NAME,
    };
    Map<String, ModuleGalleryEntry> entryMap = moduleTypesProviders.stream().collect(toMap(ModuleGalleryEntry::getName, c -> c));

    List<ModuleGalleryEntry> result = new ArrayList<>();
    for (String name : orderedNames) {
      ModuleGalleryEntry entry = entryMap.remove(name);
      if (entry != null) {
        result.add(entry);
      }
    }

    List<ModuleGalleryEntry> secondHalf = new ArrayList<>(entryMap.values());
    Collections.sort(secondHalf, Comparator.comparing(ModuleGalleryEntry::getName));

    result.addAll(secondHalf);
    return result;
  }
}

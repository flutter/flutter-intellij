/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.IconUtil;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.project.FlutterProjectModel;
import io.flutter.project.FlutterProjectStep;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FlutterDescriptionProvider implements ModuleDescriptionProvider {

  @SuppressWarnings("override") // Used in 3.0-3.2
  public Collection<? extends ModuleGalleryEntry> getDescriptions() {
    return getGalleryList(false);
  }

  @SuppressWarnings("override") // For 3.3
  public Collection<? extends ModuleGalleryEntry> getDescriptions(Project project) {
    return getGalleryList(false);
  }

  public static List<FlutterGalleryEntry> getGalleryList(boolean isNewProject) {
    boolean projectHasFlutter = isNewProject;
    OptionalValueProperty<FlutterProjectModel> sharedModel = new OptionalValueProperty<>();
    ArrayList<FlutterGalleryEntry> res = new ArrayList<>();
    if (!isNewProject) {
      IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
      Project project = frame == null ? null : frame.getProject();
      if (project == null) return res;
      for (Module module : FlutterModuleUtils.getModules(project)) {
        if (FlutterModuleUtils.isFlutterModule(module)) {
          projectHasFlutter = true;
          break;
        }
      }
    }
    if (projectHasFlutter) {
      // Makes no sense to add Flutter templates to Android projects...
      if (isNewProject) {
        res.add(new FlutterApplicationGalleryEntry(sharedModel));
      }
      res.add(new FlutterPluginGalleryEntry(sharedModel));
      res.add(new FlutterPackageGalleryEntry(sharedModel));
      if (isNewProject) {
        res.add(new FlutterModuleGalleryEntry(sharedModel));
      }
    }
    else {
      // isNewProject == false
      res.add(new ImportFlutterModuleGalleryEntry(sharedModel));
    }
    return res;
  }

  /**
   * Each type of Flutter project is represented by a subclass of this class.
   */
  public static abstract class FlutterGalleryEntry implements ModuleGalleryEntry {

    // Using an optional value because the model cannot be created until after the gallery entry is initialized.
    private OptionalValueProperty<FlutterProjectModel> mySharedModel;

    private FlutterGalleryEntry(OptionalValueProperty<FlutterProjectModel> sharedModel) {
      mySharedModel = sharedModel;
    }

    protected FlutterProjectModel model(NewModuleModel npwModel, FlutterProjectType type) {
      if (!mySharedModel.isPresent().get()) {
        mySharedModel.setValue(new FlutterModuleModel(type));
        mySharedModel.getValue().project().setValue(npwModel.getProject().getValue());
      }
      return mySharedModel.getValue();
    }

    @NotNull
    abstract public FlutterProjectStep createFlutterStep(FlutterProjectModel model);

    @Nullable
    abstract public String getHelpText();

    @Override
    public String toString() {
      return getName();
    }
  }

  private static class FlutterApplicationGalleryEntry extends FlutterGalleryEntry {

    private FlutterApplicationGalleryEntry(OptionalValueProperty<FlutterProjectModel> sharedModel) {
      super(sharedModel);
    }

    @Nullable
    @Override
    public Image getIcon() {
      return IconUtil.toImage(FlutterIcons.AndroidStudioNewProject);
    }

    @NotNull
    @Override
    public String getName() {
      return FlutterBundle.message("module.wizard.app_title");
    }

    @Nullable
    @Override // Not used by Flutter.
    public String getDescription() {
      return FlutterBundle.message("module.wizard.app_description");
    }

    @Nullable
    @Override
    public String getHelpText() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.app");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new FlutterModuleStep(
        model(model, FlutterProjectType.APP),
        FlutterBundle.message("module.wizard.app_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.APP);
    }

    @NotNull
    @Override
    public FlutterProjectStep createFlutterStep(FlutterProjectModel model) {
      return new FlutterProjectStep(
        model, FlutterBundle.message("module.wizard.app_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.APP);
    }
  }

  private static class FlutterPackageGalleryEntry extends FlutterGalleryEntry {

    private FlutterPackageGalleryEntry(OptionalValueProperty<FlutterProjectModel> sharedModel) {
      super(sharedModel);
    }

    @Nullable
    @Override
    public Image getIcon() {
      return IconUtil.toImage(FlutterIcons.AndroidStudioNewPackage);
    }

    @NotNull
    @Override
    public String getName() {
      return FlutterBundle.message("module.wizard.package_title");
    }

    @Nullable
    @Override // Not used by Flutter.
    public String getDescription() {
      return FlutterBundle.message("module.wizard.package_description");
    }

    @Nullable
    @Override
    public String getHelpText() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.package");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new FlutterModuleStep(
        model(model, FlutterProjectType.PACKAGE),
        FlutterBundle.message("module.wizard.package_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.PACKAGE);
    }

    @NotNull
    @Override
    public FlutterProjectStep createFlutterStep(@NotNull FlutterProjectModel model) {
      return new FlutterProjectStep(
        model, FlutterBundle.message("module.wizard.package_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.PACKAGE);
    }
  }

  private static class FlutterPluginGalleryEntry extends FlutterGalleryEntry {

    private FlutterPluginGalleryEntry(OptionalValueProperty<FlutterProjectModel> sharedModel) {
      super(sharedModel);
    }

    @Nullable
    @Override
    public Image getIcon() {
      return IconUtil.toImage(FlutterIcons.AndroidStudioNewPlugin);
    }

    @NotNull
    @Override
    public String getName() {
      return FlutterBundle.message("module.wizard.plugin_title");
    }

    @Nullable
    @Override // Not used by Flutter.
    public String getDescription() {
      return FlutterBundle.message("module.wizard.plugin_description");
    }

    @Nullable
    @Override
    public String getHelpText() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.plugin");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new FlutterModuleStep(
        model(model, FlutterProjectType.PLUGIN),
        FlutterBundle.message("module.wizard.plugin_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.PLUGIN);
    }

    @NotNull
    @Override
    public FlutterProjectStep createFlutterStep(@NotNull FlutterProjectModel model) {
      return new FlutterProjectStep(
        model, FlutterBundle.message("module.wizard.plugin_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.PLUGIN);
    }
  }

  private static class FlutterModuleGalleryEntry extends FlutterGalleryEntry {

    private FlutterModuleGalleryEntry(OptionalValueProperty<FlutterProjectModel> sharedModel) {
      super(sharedModel);
    }

    @Nullable
    @Override
    public Image getIcon() {
      return IconUtil.toImage(FlutterIcons.AndroidStudioNewModule);
    }

    @NotNull
    @Override
    public String getName() {
      return FlutterBundle.message("module.wizard.module_title");
    }

    @Nullable
    @Override // Not used by Flutter.
    public String getDescription() {
      return FlutterBundle.message("module.wizard.module_description");
    }

    @Nullable
    @Override
    public String getHelpText() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.module");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new FlutterModuleStep(
        model(model, FlutterProjectType.MODULE),
        FlutterBundle.message("module.wizard.module_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.MODULE);
    }

    @NotNull
    @Override
    public FlutterProjectStep createFlutterStep(@NotNull FlutterProjectModel model) {
      return new FlutterProjectStep(
        model, FlutterBundle.message("module.wizard.module_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.MODULE);
    }
  }

  private static class ImportFlutterModuleGalleryEntry extends FlutterGalleryEntry {

    private ImportFlutterModuleGalleryEntry(OptionalValueProperty<FlutterProjectModel> sharedModel) {
      super(sharedModel);
    }

    @Nullable
    @Override
    public Image getIcon() {
      return IconUtil.toImage(FlutterIcons.AndroidStudioNewModule);
    }

    @NotNull
    @Override
    public String getName() {
      return FlutterBundle.message("module.wizard.import_module_title");
    }

    @Nullable
    @Override // Not used by Flutter.
    public String getDescription() {
      return FlutterBundle.message("module.wizard.import_module_description");
    }

    @Nullable
    @Override
    public String getHelpText() {
      return FlutterBundle.message("flutter.module.import.settings.help.description");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new ImportFlutterModuleStep(
        model(model, FlutterProjectType.IMPORT),
        FlutterBundle.message("module.wizard.import_module_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.IMPORT);
    }

    @NotNull
    @Override  // not used
    public FlutterProjectStep createFlutterStep(@NotNull FlutterProjectModel model) {
      return new FlutterProjectStep(
        model, FlutterBundle.message("module.wizard.import_module_step_title"),
        FlutterIcons.Flutter_64, FlutterProjectType.IMPORT);
    }
  }
}

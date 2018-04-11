/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.project.FlutterProjectModel;
import io.flutter.project.FlutterProjectStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FlutterDescriptionProvider implements ModuleDescriptionProvider {

  public static List<FlutterGalleryEntry> getGalleryList() {
    OptionalValueProperty<FlutterProjectModel> sharedModel = new OptionalValueProperty<>();
    ArrayList<FlutterGalleryEntry> res = new ArrayList<>();
    res.add(new FlutterApplicationGalleryEntry(sharedModel));
    res.add(new FlutterPluginGalleryEntry(sharedModel));
    res.add(new FlutterPackageGalleryEntry(sharedModel));
    return res;
  }

  @Override
  public Collection<? extends ModuleGalleryEntry> getDescriptions() {
    return getGalleryList();
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
    public Icon getIcon() {
      return FlutterIcons.AndroidStudioNewModule;
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
    public Icon getIcon() {
      return FlutterIcons.AndroidStudioNewPackage;
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
    public Icon getIcon() {
      return FlutterIcons.AndroidStudioNewPlugin;
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
}

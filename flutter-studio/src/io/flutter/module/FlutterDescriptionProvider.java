/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.NewModuleModel;
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

  public static List<ModuleGalleryEntry> getGalleryList(boolean includeApp) {
    ArrayList<ModuleGalleryEntry> res = new ArrayList<>();
    if (includeApp) {
      res.add(new FlutterApplicationGalleryEntry());
    }
    res.add(new FlutterPluginGalleryEntry());
    res.add(new FlutterPackageGalleryEntry());
    return res;
  }

  @Override
  public Collection<? extends ModuleGalleryEntry> getDescriptions() {
    return getGalleryList(false);
  }

  /**
   * Each type of Flutter project is represented by a class that implements this interface.
   */
  public interface FlutterGalleryEntry extends ModuleGalleryEntry {

    // TODO(messick): Unify module and project creation so we don't need this method.
    @NotNull
    FlutterProjectStep createFlutterStep(FlutterProjectModel model);

    @Nullable
    String getHelpText();
  }

  private static class FlutterApplicationGalleryEntry implements FlutterGalleryEntry {

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
    @Override
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
      return new FlutterPackageStep(
        new FlutterModuleModel(model.getProject().getValue()),
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

  private static class FlutterPackageGalleryEntry implements FlutterGalleryEntry {

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
    @Override
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
      return new FlutterPackageStep(
        new FlutterModuleModel(model.getProject().getValue()),
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

  private static class FlutterPluginGalleryEntry implements FlutterGalleryEntry {

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
    @Override
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
      return new FlutterPackageStep(
        new FlutterModuleModel(model.getProject().getValue()),
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
